import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, retry, switchMap, throwError, timer } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { NotificationService } from '../services/notification.service';
import { ApiError, errorCodeToI18nKey } from '../models/api-error.model';
import { SKIP_ERROR_TOAST } from './http-context';

const AUTH_ENDPOINTS = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/refresh',
  '/api/auth/forgot-password',
  '/api/auth/reset-password',
];

const NETWORK_RETRY_COUNT = 2;
const NETWORK_RETRY_DELAY_MS = 800;

/**
 * status 0 means the browser never got a response at all - connection refused,
 * DNS failure, or (the common case here) the machine/Docker Desktop was asleep
 * and the socket died. 502/503/504 mean a gateway or upstream was briefly down,
 * e.g. mid-restart. Neither case is a real API error, so silently retrying
 * before surfacing anything to the user is safe and avoids a spurious "Une
 * erreur inattendue est survenue" for what's actually a transient blip.
 */
function isTransientNetworkError(error: unknown): boolean {
  if (!(error instanceof HttpErrorResponse)) return false;
  return error.status === 0 || error.status === 502 || error.status === 503 || error.status === 504;
}

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const notification = inject(NotificationService);

  return next(req).pipe(
    // Only GET requests are retried - they're idempotent, so replaying one after
    // a dropped connection can't create a duplicate dataset, trigger a second
    // analysis, etc. A mutating request (POST/PUT/PATCH/DELETE) that hits a
    // transient error surfaces immediately instead, via the catchError below.
    retry({
      count: req.method === 'GET' ? NETWORK_RETRY_COUNT : 0,
      delay: (error, retryCount) => {
        if (!isTransientNetworkError(error)) {
          throw error;
        }
        return timer(NETWORK_RETRY_DELAY_MS * retryCount);
      },
    }),
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const isAuthEndpoint = AUTH_ENDPOINTS.some((path) => req.url.includes(path));

        // A 401 on any endpoint other than the auth ones means the access token
        // expired - silently exchange it for a fresh one via the refresh token
        // and retry the original request once, instead of forcing a re-login
        // every 15 minutes. Only if the refresh itself fails do we log out.
        if (error.status === 401 && !isAuthEndpoint) {
          return authService.refreshAccessToken().pipe(
            switchMap((newToken) => next(req.clone({ setHeaders: { Authorization: `Bearer ${newToken}` } }))),
            catchError((refreshError) => {
              authService.logout();
              return throwError(() => refreshError);
            }),
          );
        }

        if (!req.context.get(SKIP_ERROR_TOAST)) {
          const body = error.error as ApiError | undefined;
          notification.error(errorCodeToI18nKey(body?.code));
        }
      }
      return throwError(() => error);
    }),
  );
};
