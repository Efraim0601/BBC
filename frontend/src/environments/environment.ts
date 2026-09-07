export const environment = {
  production: false,
  native: false,
  // Same-origin in dev thanks to proxy.conf.json; in prod Nginx proxies /api and /ws.
  apiUrl: '/api',
  wsUrl: '/ws',
};
