import { Injectable, effect, signal } from '@angular/core';
import { EMPTY, catchError, filter, interval, startWith, switchMap } from 'rxjs';
import { AuthService } from './auth.service';
import { ReportService } from './report.service';
import { QualityReport } from '../models/report.model';

const POLL_INTERVAL_MS = 60_000;
const FEED_LIMIT = 10;

/**
 * Backs the notification bell with periodic polling of the existing reports
 * endpoint - there is no WebSocket/SSE transport in this stack, so "recent"
 * means "as of the last poll" rather than a live push. Reusing GET /api/reports
 * (already visible to any authenticated user, unlike the admin-only audit log)
 * avoids a new backend endpoint for what is fundamentally the same data the
 * Reports page already shows.
 */
@Injectable({ providedIn: 'root' })
export class NotificationCenterService {
  recentReports = signal<QualityReport[]>([]);
  unreadCount = signal(0);

  // Tracks generatedAt rather than id: the sort order the backend itself
  // guarantees (findAllByOrderByGeneratedAtDesc) is by timestamp, not id.
  private latestKnownGeneratedAt: string | null = null;

  constructor(
    private readonly authService: AuthService,
    private readonly reportService: ReportService,
  ) {
    // A root-provided singleton's subscriptions outlive any single component,
    // including MainLayout across a logout/login cycle - so this must survive
    // for the whole tab lifetime rather than being torn down and recreated.
    // Skipping polls while logged out avoids both a pointless 401 on every
    // tick and (via catchError) that 401 permanently killing the interval,
    // which would otherwise leave notifications silently dead after the next
    // login.
    interval(POLL_INTERVAL_MS)
      .pipe(
        startWith(0),
        filter(() => this.authService.isAuthenticated()),
        switchMap(() =>
          this.reportService.getPage(0, FEED_LIMIT).pipe(catchError(() => EMPTY)),
        ),
      )
      .subscribe((result) => this.handlePoll(result.content));

    // Clears out whatever the previous session accumulated as soon as the user
    // logs out - without this, a different account signing in later in the
    // same tab (shared machine) would briefly see the prior user's activity
    // feed until the next poll overwrote it.
    effect(() => {
      if (!this.authService.isAuthenticated()) {
        this.recentReports.set([]);
        this.unreadCount.set(0);
        this.latestKnownGeneratedAt = null;
      }
    });
  }

  markAllRead(): void {
    this.unreadCount.set(0);
  }

  private handlePoll(reports: QualityReport[]): void {
    this.recentReports.set(reports);
    if (reports.length === 0) return;

    const latestInBatch = reports[0].generatedAt;
    if (this.latestKnownGeneratedAt === null) {
      // First poll since the app loaded - this is existing history, not new
      // activity, so it establishes the baseline without alerting the user.
      this.latestKnownGeneratedAt = latestInBatch;
      return;
    }

    const newCount = reports.filter((r) => r.generatedAt > this.latestKnownGeneratedAt!).length;
    if (newCount > 0) {
      this.unreadCount.update((count) => count + newCount);
      this.latestKnownGeneratedAt = latestInBatch;
    }
  }
}
