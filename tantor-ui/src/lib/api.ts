// Lightweight auth + fetch helper for governance APIs (BFSI hardening layer).
const TOKEN_KEY = 'tantor.jwt';
const USER_KEY = 'tantor.user';

export interface SessionUser {
  username: string;
  role: string;
  permissions: string[];
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getSession(): SessionUser | null {
  const raw = localStorage.getItem(USER_KEY);
  return raw ? JSON.parse(raw) : null;
}

export function hasPermission(perm: string): boolean {
  const s = getSession();
  return !!s && s.permissions?.includes(perm);
}

export function logout() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

export async function login(username: string, password: string): Promise<SessionUser> {
  const res = await fetch('/api/v1/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.error || `Login failed (${res.status})`);
  }
  const data = await res.json();
  localStorage.setItem(TOKEN_KEY, data.token);
  const user: SessionUser = {
    username: data.username,
    role: data.role,
    permissions: data.permissions || [],
  };
  localStorage.setItem(USER_KEY, JSON.stringify(user));
  return user;
}

// fetch wrapper that attaches the Bearer token and surfaces 401/403 cleanly.
export async function apiFetch(input: string, init: RequestInit = {}): Promise<Response> {
  const token = getToken();
  const headers = new Headers(init.headers || {});
  if (token) headers.set('Authorization', `Bearer ${token}`);
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
  const res = await fetch(input, { ...init, headers });
  if (res.status === 401) throw new Error('Not authenticated - please sign in');
  if (res.status === 403) throw new Error('You do not have permission for this action');
  return res;
}
