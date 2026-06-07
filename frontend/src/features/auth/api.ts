import { http } from '@/services/apiClient';
import type { ApiResult, AuthSession } from '@/types';

export function registerUser(
  fullName: string,
  email: string,
  password: string,
): Promise<ApiResult<AuthSession>> {
  return http.post<AuthSession>('/api/users/register', { fullName, email, password });
}

export function loginUser(email: string, password: string): Promise<ApiResult<AuthSession>> {
  return http.post<AuthSession>('/api/users/login', { email, password });
}
