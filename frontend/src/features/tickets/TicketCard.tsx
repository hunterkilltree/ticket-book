import type { Ticket } from '@/types';

export function TicketCard({ ticket }: { ticket: Ticket }) {
  return (
    <div className="card ticket-card">
      <div className="ticket-qr" title={ticket.qrCode}>{ticket.qrCode}</div>
      <div>
        <p className="muted">Seat {ticket.seatId.slice(0, 8)}…</p>
        <p>Status: {ticket.status}</p>
      </div>
    </div>
  );
}
