export const environment = {
  production: true,
  // Empty = same-origin relative requests (e.g. "/api/auth/login"). The bundled
  // nginx.conf reverse-proxies /api and /internal to the backend container, so
  // the browser never needs a cross-origin request or CORS in production.
  apiUrl: '',
};
