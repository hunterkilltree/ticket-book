export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'REFUNDED';

export interface Order {
  id: string;
  userId: string;
  totalAmount: number;
  status: OrderStatus;
}
