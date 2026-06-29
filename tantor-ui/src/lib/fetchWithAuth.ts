import { getToken } from './api';

const nativeFetch = window.fetch.bind(window);

window.fetch = async (input: RequestInfo | URL, init: RequestInit = {}) => {
  const token = getToken();
  const headers = new Headers(init.headers || {});
  const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;

  if (token && url.includes('/api/v1/') && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await nativeFetch(input, { ...init, headers });
  if (response.status === 401 && !url.includes('/api/v1/auth/login')) {
    // Keep UX simple and safe: force a fresh login when a token expires or is missing.
    localStorage.removeItem('tantor.jwt');
    localStorage.removeItem('tantor.user');
    if (window.location.pathname !== '/login') {
      window.location.href = '/login';
    }
  }
  return response;
};
