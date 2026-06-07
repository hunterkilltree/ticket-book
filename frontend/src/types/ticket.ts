export type TicketStatus = 'ISSUED' | 'USED' | 'CANCELLED';

export interface Ticket {
  id: string;
  orderId: string;
  seatId: string;
  qrCode: string;
  status: TicketStatus;
}
