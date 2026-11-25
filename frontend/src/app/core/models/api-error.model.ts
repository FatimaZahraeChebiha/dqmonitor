/**
 * Mirrors the backend's ErrorResponse envelope. `code` and field-level error `code`s
 * are stable, machine-readable identifiers (never localized text from the server) -
 * the frontend maps them to a translation key via errorCodeToI18nKey().
 */
export interface ApiFieldError {
  field: string;
  code: string;
}

export interface ApiError {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  fieldErrors: ApiFieldError[];
}

export function errorCodeToI18nKey(code: string | undefined): string {
  if (!code) {
    return 'errors.INTERNAL_ERROR';
  }
  return `errors.${code}`;
}
