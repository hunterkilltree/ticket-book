const KEY = 'tb.token';
let token: string | null =
  typeof localStorage !== 'undefined' ? localStorage.getItem(KEY) : null;

export function getToken(): string | null {
  return token;
}

export function setToken(next: string | null): void {
  token = next;
  if (typeof localStorage === 'undefined') return;
  if (next) localStorage.setItem(KEY, next);
  else localStorage.removeItem(KEY);
}
