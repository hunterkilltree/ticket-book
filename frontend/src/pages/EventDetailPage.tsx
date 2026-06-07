import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import type { Event, ProblemDetail } from '@/types';
import { getEvent } from '@/features/catalog/api';
import { formatDateTime } from '@/utils/date';
import { Button } from '@/components/Button';

export function EventDetailPage() {
  const { eventId = '' } = useParams();
  const [event, setEvent] = useState<Event | null>(null);
  const [error, setError] = useState<ProblemDetail | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    getEvent(eventId).then((res) => {
      if (!active) return;
      if (res.ok) setEvent(res.data);
      else setError(res.error);
      setLoading(false);
    });
    return () => {
      active = false;
    };
  }, [eventId]);

  if (loading) return <p>Loading…</p>;
  if (error || !event) return <p className="error-text">Event not found.</p>;

  return (
    <section>
      <p>
        <Link to="/events">← Back to events</Link>
      </p>
      <h1>{event.title}</h1>
      <p className="muted">{event.artist}</p>
      <p>{formatDateTime(event.startsAt)}</p>
      <p className="muted">
        {event.venue.name} — {event.venue.address}
      </p>
      <Link to={`/events/${event.id}/seats`}>
        <Button>Pick seats</Button>
      </Link>
    </section>
  );
}
