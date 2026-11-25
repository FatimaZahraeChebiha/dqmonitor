import { Injectable, computed, signal } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, finalize, map, shareReplay, tap, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ForgotPasswordRequest,
  LoginRequest,
  LoginResponse,
  RefreshResponse,
  RegisterRequest,
  ResetPasswordRequest,
} from '../models/auth.model';
import { User } from '../models/user.model';
import { SKIP_ERROR_TOAST } from '../interceptors/http-context';

const STORAGE_KEY = 'dqmonitor.session';

interface StoredSession {
  token: string;
  refreshToken: string;
  user: User;
  expiresAt: number;
}

/**
 * Session kept in memory (signals) and mirrored to sessionStorage rather than
 * localStorage - it survives a page refresh within the tab but clears when the
 * tab closes, instead of persisting indefinitely like the original design.
 *
 * Access tokens are short-lived (15 min); the refresh token (7 days, opaque,
 * revocable server-side) is what actually gates "is this session alive" - the
 * error interceptor transparently exchanges an expired access token for a new
 * one via refreshAccessToken() rather than forcing a re-login every 15 minutes.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly tokenSignal = signal<string | null>(null);
  private readonly refreshTokenSignal = signal<string | null>(null);
  private readonly userSignal = signal<User | null>(null);
  private expiresAt = 0;
  private refreshInFlight$: Observable<string> | null = null;

  readonly currentUser = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => !!this.refreshTokenSignal());
  readonly isAdmin = computed(() => this.userSignal()?.role === 'ADMIN');

  constructor(
    private readonly http: HttpClient,
    private readonly router: Router,
  ) {
    this.restoreSession();
  }

  login(request: LoginRequest): Observable<LoginResponse> {
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    return this.http
      .post<LoginResponse>(`${environment.apiUrl}/api/auth/login`, request, { context })
      .pipe(tap((response) => this.setSession(response)));
  }

  register(request: RegisterRequest): Observable<User> {
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    return this.http.post<User>(`${environment.apiUrl}/api/auth/register`, request, { context });
  }

  forgotPassword(request: ForgotPasswordRequest): Observable<void> {
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    return this.http.post<void>(`${environment.apiUrl}/api/auth/forgot-password`, request, { context });
  }

  resetPassword(request: ResetPasswordRequest): Observable<void> {
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    return this.http.post<void>(`${environment.apiUrl}/api/auth/reset-password`, request, { context });
  }

  /** Shares one in-flight refresh across concurrent 401s instead of firing one per request. */
  refreshAccessToken(): Observable<string> {
    if (this.refreshInFlight$) {
      return this.refreshInFlight$;
    }
    const refreshToken = this.refreshTokenSignal();
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token available'));
    }

    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    this.refreshInFlight$ = this.http
      .post<RefreshResponse>(`${environment.apiUrl}/api/auth/refresh`, { refreshToken }, { context })
      .pipe(
        tap((response) => {
          this.tokenSignal.set(response.token);
          this.refreshTokenSignal.set(response.refreshToken);
          this.expiresAt = Date.now() + response.expiresInMs;
          this.persistSession();
        }),
        map((response) => response.token),
        finalize(() => {
          this.refreshInFlight$ = null;
        }),
        shareReplay(1),
      );
    return this.refreshInFlight$;
  }

  logout(): void {
    const refreshToken = this.refreshTokenSignal();
    const finish = () => this.clearSession();

    if (refreshToken) {
      const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
      this.http
        .post(`${environment.apiUrl}/api/auth/logout`, { refreshToken }, { context })
        .subscribe({ next: finish, error: finish });
    } else {
      finish();
    }
  }

  getToken(): string | null {
    return this.tokenSignal();
  }

  updateCurrentUser(user: User): void {
    this.userSignal.set(user);
    this.persistSession();
  }

  private clearSession(): void {
    this.tokenSignal.set(null);
    this.refreshTokenSignal.set(null);
    this.userSignal.set(null);
    this.expiresAt = 0;
    sessionStorage.removeItem(STORAGE_KEY);
    this.router.navigate(['/login']);
  }

  private setSession(response: LoginResponse): void {
    this.tokenSignal.set(response.token);
    this.refreshTokenSignal.set(response.refreshToken);
    this.userSignal.set(response.user);
    this.expiresAt = Date.now() + response.expiresInMs;
    this.persistSession();
  }

  private persistSession(): void {
    const token = this.tokenSignal();
    const refreshToken = this.refreshTokenSignal();
    const user = this.userSignal();
    if (!token || !refreshToken || !user) {
      return;
    }
    const session: StoredSession = { token, refreshToken, user, expiresAt: this.expiresAt };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }

  private restoreSession(): void {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return;
    }
    try {
      const session = JSON.parse(raw) as StoredSession;
      // Restore even if the access token itself has expired - the refresh token
      // is what matters here, and the error interceptor will silently exchange
      // it for a fresh access token on the first 401.
      this.tokenSignal.set(session.token);
      this.refreshTokenSignal.set(session.refreshToken);
      this.userSignal.set(session.user);
      this.expiresAt = session.expiresAt;
    } catch {
      sessionStorage.removeItem(STORAGE_KEY);
    }
  }
}
