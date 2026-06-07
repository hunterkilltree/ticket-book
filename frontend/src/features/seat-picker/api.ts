import { http } from '@/services/apiClient';
import type { ApiResult, Seat } from '@/types';

export interface Reservation {
  reservedSeatIds: string[];
  failedSeatIds: string[];
  expiresAt: string;
}

export function listSeats(eventId: string): Promise<ApiResult<Seat[]>> {
  return http.get<Seat[]>(`/api/bookings/events/${eventId}/seats`);
}

export function reserveSeats(
  eventId: string,
  seatIds: string[],
  userId?: string,
): Promise<ApiResult<Reservation>> {
  return http.post<Reservation>('/api/bookings/reserve', { eventId, seatIds, userId });
}
