import axios from 'axios';
import type { ApiResult } from '@/types';
import { getToken } from './authToken';
import { toProblemDetail } from './errors';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  headers: { 'Content-Type': 'application/json' },
});

// Attach JWT to every request when present.
api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

/** Wrap an Axios call into a typed discriminated ApiResult — no raw throws in components. */
async function request<T>(fn: () => Promise<{ data: T }>): Promise<ApiResult<T>> {
  try {
    const { data } = await fn();
    return { ok: true, data };
  } catch (err) {
    return { ok: false, error: toProblemDetail(err) };
  }
}

export const http = {
  get: <T>(url: string, params?: unknown) => request<T>(() => api.get<T>(url, { params })),
  post: <T>(url: string, body?: unknown, headers?: Record<string, string>) =>
    request<T>(() => api.post<T>(url, body, { headers })),
  put: <T>(url: string, body?: unknown) => request<T>(() => api.put<T>(url, body)),
  del: <T>(url: string) => request<T>(() => api.delete<T>(url)),
};

export { api };
