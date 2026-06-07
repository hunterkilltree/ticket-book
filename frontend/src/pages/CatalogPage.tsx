import { useState } from 'react';
import { Input } from '@/components/Input';
import { EventCard } from '@/features/catalog/EventCard';
import { useEvents } from '@/features/catalog/useEvents';

export function CatalogPage() {
  const [search, setSearch] = useState('');
  const { events, loading, error } = useEvents(search);

  return (
    <section>
      <h1>Browse events</h1>
      <Input
        placeholder="Search by title or artist…"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        aria-label="Search events"
      />
      {loading && <p>Loading…</p>}
      {error && <p className="error-text">Couldn’t load events: {error.title}</p>}
      {!loading && !error && events.length === 0 && <p>No events found.</p>}
      <div className="event-grid">
        {events.map((ev) => (
          <EventCard key={ev.id} event={ev} />
        ))}
      </div>
    </section>
  );
}
