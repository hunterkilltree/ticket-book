import { http } from '@/services/apiClient';
import type { ApiResult, Order } from '@/types';

export interface Payment {
  id: string;
  orderId: string;
  status: string;
}

export function createOrder(
  seatIds: string[],
  userId: string | undefined,
  idempotencyKey: string,
): Promise<ApiResult<Order>> {
  return http.post<Order>('/api/orders', { userId, seatIds }, { 'Idempotency-Key': idempotencyKey });
}

export function payOrder(
  orderId: string,
  seatIds: string[],
  amount: number,
  eventId: string | undefined,
  userId: string | undefined,
  idempotencyKey: string,
): Promise<ApiResult<Payment>> {
  return http.post<Payment>(
    '/api/payments',
    { orderId, seatIds, amount, eventId, userId },
    { 'Idempotency-Key': idempotencyKey },
  );
}
