# Architecture

This document covers the non-obvious decisions behind DQMonitor's design — the
things you'd have to read a lot of code to reconstruct otherwise. For "how do I
run this," see the [README](../README.md).

## Service boundaries

```
frontend (Angular SPA)  ──JWT bearer──>  backend (Spring Boot)  ──Flyway──>  MySQL
                                              │
                                              │  HTTP (Docker) or ProcessBuilder (local dev)
                                              ▼
                                    data-processing (FastAPI + PySpark + Great Expectations)
                                              │
                                              │  POST /internal/reports (X-Internal-Key)
                                              ▼
                                    backend persists the report
```

Three services, three separate schema/data owners:

- **backend** owns the MySQL schema exclusively (via JPA + Flyway). Nothing else
  writes to the database directly.
- **data-processing** owns no credentials at all. It fetches the active
  validation rules for a dataset from the backend, runs Great Expectations
  against the file, and POSTs the result back over HTTP. This was a deliberate
  fix for an earlier design where the Python job wrote to MySQL directly with
  its own hardcoded credentials — that duplicated schema ownership and made the
  row-validity count a percentage guess instead of a real measurement.
- **frontend** never talks to MySQL or the analysis service directly — only the
  backend's REST API, either cross-origin in dev (`environment.apiUrl`) or via
  nginx's same-origin `/api` reverse proxy in production (avoiding CORS
  entirely in the deployed configuration).

### Dual-mode analysis trigger

`DatasetService.analyzeDataset()` checks `app.analysis.service-url`
(`ANALYSIS_SERVICE_URL` env var):

- **Set** (Docker Compose sets it to `http://data-processing:8001`): POSTs to
  the FastAPI service's `/analyze`, which runs the job in a background thread
  and returns `202` immediately — fire-and-forget, matching the original
  `ProcessBuilder` semantics.
- **Empty** (local dev without Docker): spawns `quality_check.py` directly via
  `ProcessBuilder`, streaming its stdout to the backend's log.

Same backend code path either way — the branch is decided once per call, not
duplicated logic. This let local development keep working exactly as before
while Docker Compose gained the "real" microservice architecture.

**A subtle bug this surfaced**: Spring's default `RestClient` (built on the JDK's
`java.net.http.HttpClient`) sends `Expect: 100-continue` for POST bodies, which
`uvicorn` doesn't answer — the client would give up and send the request with
no body, and FastAPI reported "field required" even though the JSON was
correctly serialized. Fixed by building the `RestClient` on
`SimpleClientHttpRequestFactory` (`HttpURLConnection`-based) instead, which
doesn't use that handshake. This only surfaces when actually running the
containerized call end-to-end — a good reminder that the `docker-compose up`
verification step catches classes of bugs that isolated unit/local testing
cannot.

**A second subtle threading bug, found the same way**: after adding Airflow as a
second caller of `/analyze`, jobs it triggered started failing with Great
Expectations' Spark calls raising `SparkPlan.session() is null`, while the exact
same script run directly (no FastAPI, no threading) succeeded. Root cause: the
sync `def analyze(...)` handler is offloaded by Starlette onto its own anyio
threadpool worker thread, and spawning the Spark job's background
`threading.Thread` *from that already-nested worker thread* breaks py4j's
per-thread JVM connection tracking - Great Expectations' internal Spark calls
then run on a JVM-side thread with no active `SparkSession` bound to it. Making
the handler `async def` keeps it on the event loop (main) thread, so the
spawned job thread is only one level of nesting deep instead of two - matching
the working direct-script case. Another instance of a bug that only reproduces
end-to-end, not in isolated unit tests.

## AI Assistant

`LlmProvider` is a one-method interface (`complete(systemPrompt, history)`)
with three implementations - `OllamaLlmProvider`, `AnthropicLlmProvider` (the
official `anthropic-java` SDK), `OpenAiLlmProvider` - selected by a single
property, `app.ai.provider` (`AI_PROVIDER` env var). Ollama is the default so
the feature is free and works offline in local development; switching to a
hosted provider in production is an env var change, not a code change.

Context is retrieved with plain SQL, not embeddings: the platform's data is
structured rows (a dashboard summary, active validation rules, dataset/report
lookups), not a free-text document corpus, so a vector store would add
infrastructure without adding retrieval quality. `AiContextService` always
includes the dashboard summary and active rules, and adds either the named
dataset's latest report (if the user's message matches an uploaded filename)
or a handful of recent reports as a fallback.

## Scheduled analysis (Airflow)

`airflow/dags/scheduled_quality_analysis.py` runs every 15 minutes and calls
two small internal endpoints (`InternalController`, same `X-Internal-Key` guard
as the Python job's endpoints): `GET /internal/datasets/pending` to find
datasets stuck in `PENDING`, then `POST /internal/datasets/{id}/analyze` for
each - the exact same `DatasetService.analyzeDataset()` path the "Analyze"
button calls. Airflow is a scheduler on top of that existing trigger, not a
second implementation of it; the DAG itself has no analysis logic. Runs in
`standalone` mode (webserver + scheduler + triggerer in one container, SQLite
metadata DB) - a separate Postgres metadata store would be pure overhead at
this project's scale.

## Ops dashboards (Grafana)

Grafana reads MySQL directly through its own least-privilege account: a
`grafana` user with a database-level `GRANT SELECT` (provisioned by
`mysql-init/01-grafana-readonly-user.sh`, which runs once at MySQL's first
container init - a plain `.sql` file there can't expand environment variables,
so provisioning the password-bearing `CREATE USER` needed a shell script
instead). The grant is schema-level, not per-table, so it's issued *before*
Flyway has created any tables and still covers everything Flyway adds later.

The data source and dashboard (`grafana/provisioning/`) are provisioned on
container start - no manual click-through setup. The dashboard is deliberately
a separate, ops-facing view (dataset throughput and status mix, active
rules/users, recent audit activity) rather than a re-plot of the in-app
Dashboard's end-user KPIs - Grafana answers "is the platform healthy," the
in-app charts answer "is my data good."

**One MySQL gotcha worth flagging**: `SELECT CAST(DATE(uploaded_at) AS DATETIME) AS time, COUNT(*) ... GROUP BY DATE(uploaded_at)`
fails under MySQL 8's default `only_full_group_by` mode - the `CAST(...)`
wrapper around the `GROUP BY` expression isn't recognized as functionally
dependent on it, even though it obviously is. The fix is to `GROUP BY` the
exact same expression that appears in `SELECT`, not just an equivalent one.

## Security model

- **Passwords**: BCrypt, never logged or returned in any DTO (`UserResponse`
  has no `password` field, unlike the raw entity).
- **Access tokens**: short-lived (15 min) signed JWTs (HS512), carrying
  `userId`/`role` claims, verified by `JwtAuthFilter` on every request.
- **Refresh tokens**: opaque random UUIDs stored in the `refresh_tokens` table,
  not self-contained JWTs — this is what makes them revocable (logout, password
  reset invalidates every session) in a way a stateless refresh JWT wouldn't
  be. Rotated on every use (`RefreshTokenService.rotate`): the old token is
  marked revoked and a new one issued, so a stolen-but-unused refresh token
  becomes single-use-detectable.
- **Rate limiting**: `LoginAttemptService` (Caffeine, in-memory) tracks failed
  attempts per normalized email *and* per source IP separately — per-account
  catches distributed attacks against one user, per-IP catches one attacker
  cycling through many emails. Single-instance only by design; a multi-replica
  deployment would need to move this to Redis (noted in the class Javadoc
  rather than silently breaking later).
- **Authorization**: `@PreAuthorize("hasRole('ADMIN')")` enforced server-side on
  every admin-only endpoint — the frontend's `adminGuard` is a UX convenience,
  not the actual security boundary.
- **Internal service-to-service calls**: `/internal/**` is protected by a
  shared-secret header (`X-Internal-Key`, checked by `InternalApiKeyFilter`),
  not JWT — this is a trusted loopback contract between the backend and the
  Python job, not a browser-facing endpoint, and is hidden from Swagger UI
  (`@Hidden`) to keep the public API surface accurate.
- **Audit trail**: every security-relevant action (login success/failure/block,
  registration, password reset, user create/delete, database reset) is
  recorded in `audit_logs` with actor, IP, and a machine-readable `AuditAction`
  enum — never a pre-translated string, so the frontend can render it in
  whichever language the viewer has selected.

## Scalability decisions

- **Pagination everywhere**: datasets, reports, users, and audit logs are all
  `Pageable`-backed, wrapped in a generic `PageResponse<T>` DTO on the way out.
  `findAll()` on an unbounded table was the original design's ceiling.
- **Dashboard is a SQL aggregate, not a client-side reduction.** The original
  approach would have been "fetch all datasets and all reports, sum/average in
  the browser" — this doesn't scale and ships far more data than the UI needs.
  `GET /api/dashboard/summary` computes averages/counts/trends in SQL and
  returns a small fixed-shape response; a separate small paginated call
  (`getPage(0, 5)`) covers "recent activity."
- **Caching**: `@Cacheable("dashboardSummary")` (Caffeine, 2-minute TTL) with
  explicit `@CacheEvict` on every mutation that could change it (upload,
  delete, analysis completion, database reset) — short-lived enough that stale
  data is never visible for long, but avoids recomputing aggregates on every
  dashboard load.
- **User stats are computed server-side** (`GET /api/users/stats`), not derived
  from the currently-paginated page of users on the client — a paginated list
  can't accurately compute "how many admins total" from just the visible page.

## Frontend: zoneless change detection

Angular 22 defaults new apps to **zoneless** change detection (no `zone.js`),
which only re-renders a component when a signal it reads changes, or
`markForCheck()`/an event from its own template is triggered. This has a sharp
edge: a component that mutates a **plain field** (not a signal) inside an
RxJS `subscribe()` callback will update its internal state correctly but never
repaint, because nothing tells Angular to re-check it.

This was found the hard way: an E2E test showed a failed login leaving the
submit button spinning forever with the error message never appearing, even
though the component's `errorKey` field was set correctly (confirmed via
`ng.getComponent()` in a debug script). `login.ts`/`register.ts` were the only
two components still using the old plain-field pattern; every other page
(dashboard, reports, datasets, validation-rules, forgot/reset-password) already
used signals correctly. Fixed by converting `errorKey`/`isSubmitting` to
signals in both, matching the rest of the codebase — not by reintroducing
`zone.js` (which was tried first and turned out not to cleanly restore classic
behavior in this Angular version anyway, since HttpClient here runs on the
Fetch backend which the core zone.js bundle doesn't patch by default).

**Practical rule for new components**: any state read in a template that gets
mutated inside an async callback (HTTP response, timer, dialog `afterClosed()`)
must be a `signal()`, or the mutation must be followed by an explicit
`ChangeDetectorRef.markForCheck()` call (used in `settings.ts` for three plain
`ngModel`-bound password fields that get reset after a successful save).

All components use `ChangeDetectionStrategy.OnPush` — safe throughout the app
specifically because that discipline is followed everywhere.

## i18n contract

The backend **never sends translated text** — only stable, machine-readable
codes (`ErrorResponse.code`, `AuditLog.action`). The frontend maps these to
Transloco keys (`errors.<CODE>`, `admin.audit.action.<ACTION>`). This keeps
translation ownership entirely in one layer and means adding a language never
touches the backend.

Arabic gets `dir="rtl"` applied at the app-shell level (not just inside
individual components), so the sidebar, tables, and dialogs all mirror
correctly together.

## Testing strategy

- **Backend unit tests** (surefire, `*Test.java`, run via `mvn test`): pure
  logic in isolation with Mockito — `JwtServiceTest`, `LoginAttemptServiceTest`,
  `AuthServiceTest`.
- **Backend integration tests** (failsafe, `*IT.java`, run via `mvn verify`):
  real HTTP + Spring Security stack against a real MySQL instance —
  `AuthControllerIT` exercises register → login → protected-endpoint access →
  refresh rotation → 403 on admin-only routes, all through actual servlet
  requests, not mocked security contexts. Kept in a separate Maven phase
  (failsafe vs. surefire) so `mvn test` stays fast for local iteration while
  CI's `mvn verify` still gets full coverage.
- **Frontend unit tests** (Vitest, via Angular's `@angular/build:unit-test`):
  services, guards, and interceptors get real behavioral tests (e.g.
  `AuthService`'s shared in-flight refresh, the auth interceptor's header
  injection); components get smoke tests with real Transloco translations
  loaded (`src/testing/transloco-testing.ts`) rather than stubbed strings, so a
  broken translation key would still surface.
- **E2E** (Playwright, `e2e/`): runs against the actually-running dev stack
  (frontend + backend + MySQL), not mocks — login/register/logout/guards, and
  a full upload → analyze → Great Expectations → report flow. This is what
  caught the zoneless change-detection bug above; a unit test with a mocked
  HTTP client would not have.

## CI/CD

`.github/workflows/ci.yml`: backend job spins up a MySQL service container and
runs `mvn verify` (both test phases), frontend job runs `npm test` + `npm run
build`, and a matrix job builds all three Docker images (backend, frontend,
data-processing) to catch Dockerfile regressions — this is exactly how a
`python:3.13-slim` base image change (Debian dropping `openjdk-17` in favor of
`openjdk-21`) was caught during local `docker-compose up` verification and
would be caught by CI going forward.
