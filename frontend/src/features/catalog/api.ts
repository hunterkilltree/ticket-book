import { http } from '@/services/apiClient';
import type { ApiResult, Event } from '@/types';

/** GET /api/events?q= — public catalog (backend defaults to PUBLISHED). */
export function listEvents(q?: string): Promise<ApiResult<Event[]>> {
  return http.get<Event[]>('/api/events', q ? { q } : undefined);
}

/** GET /api/events/{id} */
export function getEvent(id: string): Promise<ApiResult<Event>> {
  return http.get<Event>(`/api/events/${id}`);
}
