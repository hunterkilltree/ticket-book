import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import type { Ticket, ProblemDetail } from '@/types';
import { useAuthStore } from '@/features/auth/store';
import { listMyTickets } from '@/features/tickets/api';
import { TicketCard } from '@/features/tickets/TicketCard';

export function TicketsPage() {
  const user = useAuthStore((s) => s.user);
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [error, setError] = useState<ProblemDetail | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!user) return;
    setLoading(true);
    listMyTickets(user.id).then((res) => {
      if (res.ok) setTickets(res.data);
      else setError(res.error);
      setLoading(false);
    });
  }, [user]);

  if (!isAuthenticated || !user) {
    return (
      <section>
        <h1>My tickets</h1>
        <p>Please <Link to="/login">log in</Link> to view your tickets.</p>
      </section>
    );
  }

  return (
    <section>
      <h1>My tickets</h1>
      {loading && <p>Loading…</p>}
      {error && <p className="error-text">{error.title}</p>}
      {!loading && !error && tickets.length === 0 && (
        <p>No tickets yet. <Link to="/events">Browse events</Link>.</p>
      )}
      <div className="ticket-list">
        {tickets.map((t) => (
          <TicketCard key={t.id} ticket={t} />
        ))}
      </div>
    </section>
  );
}
