import { Link } from 'react-router-dom';
import type { Event } from '@/types';
import { formatDateTime } from '@/utils/date';

export function EventCard({ event }: { event: Event }) {
  return (
    <Link to={`/events/${event.id}`} className="event-card card">
      <h3>{event.title}</h3>
      <p className="muted">{event.artist}</p>
      <p>{formatDateTime(event.startsAt)}</p>
      <p className="muted">
        {event.venue.name} — {event.venue.address}
      </p>
    </Link>
  );
}
