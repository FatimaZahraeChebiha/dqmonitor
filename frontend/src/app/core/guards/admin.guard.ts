import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Reads role from AuthService's signal-backed session instead of manually
 * parsing localStorage - this is still a client-side UX gate only (the real
 * enforcement is server-side @PreAuthorize on the backend), but at least it's
 * backed by the same session state as the rest of the app instead of a
 * separate ad hoc read.
 */
export const adminGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated() && authService.isAdmin()) {
    return true;
  }

  router.navigate(['/dashboard']);
  return false;
};
