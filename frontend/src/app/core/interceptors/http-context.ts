import { HttpContextToken } from '@angular/common/http';

/**
 * Opt out of the global error toast for a request - used by Login/Register, which
 * show the error inline instead (a toast on top of an inline message is redundant).
 */
export const SKIP_ERROR_TOAST = new HttpContextToken<boolean>(() => false);
