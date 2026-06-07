import { http } from '@/services/apiClient';
import type { ApiResult, Ticket } from '@/types';

export function listMyTickets(userId: string): Promise<ApiResult<Ticket[]>> {
  return http.get<Ticket[]>('/api/tickets', { userId });
}
