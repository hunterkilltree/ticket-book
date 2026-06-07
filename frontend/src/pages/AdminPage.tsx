import { useEffect, useState, type FormEvent } from 'react';
import type { Event, Venue } from '@/types';
import { Button } from '@/components/Button';
import { Input } from '@/components/Input';
import {
  createEvent,
  createVenue,
  listAllEvents,
  listVenues,
  publishEvent,
} from '@/features/admin/api';
import { formatDateTime } from '@/utils/date';

export function AdminPage() {
  const [events, setEvents] = useState<Event[]>([]);
  const [venues, setVenues] = useState<Venue[]>([]);
  const [msg, setMsg] = useState<string | null>(null);

  // venue form
  const [vName, setVName] = useState('');
  const [vAddress, setVAddress] = useState('');
  const [vCapacity, setVCapacity] = useState(1000);

  // event form
  const [title, setTitle] = useState('');
  const [artist, setArtist] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [venueId, setVenueId] = useState('');

  async function refresh() {
    const [ev, vn] = await Promise.all([listAllEvents(), listVenues()]);
    if (ev.ok) setEvents(ev.data);
    if (vn.ok) {
      setVenues(vn.data);
      if (!venueId && vn.data.length > 0) setVenueId(vn.data[0].id);
    }
  }

  useEffect(() => {
    refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function onCreateVenue(e: FormEvent) {
    e.preventDefault();
    const res = await createVenue({ name: vName, address: vAddress, capacity: vCapacity });
    if (res.ok) {
      setMsg(`Venue "${res.data.name}" created.`);
      setVName('');
      setVAddress('');
      await refresh();
    } else setMsg(`Error: ${res.error.title}`);
  }

  async function onCreateEvent(e: FormEvent) {
    e.preventDefault();
    if (!venueId) {
      setMsg('Create a venue first.');
      return;
    }
    const iso = new Date(startsAt).toISOString();
    const res = await createEvent({ title, artist, startsAt: iso, venueId, status: 'DRAFT' });
    if (res.ok) {
      setMsg(`Event "${res.data.title}" created (DRAFT).`);
      setTitle('');
      setArtist('');
      setStartsAt('');
      await refresh();
    } else setMsg(`Error: ${res.error.title}`);
  }

  async function onPublish(id: string) {
    const res = await publishEvent(id);
    if (res.ok) {
      setMsg('Event published.');
      await refresh();
    } else setMsg(`Error: ${res.error.title}`);
  }

  return (
    <section>
      <h1>Admin — event management</h1>
      {msg && <p className="muted">{msg}</p>}

      <div className="admin-grid">
        <form className="card" onSubmit={onCreateVenue}>
          <h3>New venue</h3>
          <label>Name<Input value={vName} onChange={(e) => setVName(e.target.value)} required /></label>
          <label>Address<Input value={vAddress} onChange={(e) => setVAddress(e.target.value)} required /></label>
          <label>Capacity
            <Input type="number" min={1} value={vCapacity}
              onChange={(e) => setVCapacity(Number(e.target.value))} required />
          </label>
          <Button type="submit">Create venue</Button>
        </form>

        <form className="card" onSubmit={onCreateEvent}>
          <h3>New event</h3>
          <label>Title<Input value={title} onChange={(e) => setTitle(e.target.value)} required /></label>
          <label>Artist<Input value={artist} onChange={(e) => setArtist(e.target.value)} required /></label>
          <label>Starts at
            <Input type="datetime-local" value={startsAt}
              onChange={(e) => setStartsAt(e.target.value)} required />
          </label>
          <label>Venue
            <select className="input" value={venueId} onChange={(e) => setVenueId(e.target.value)}>
              {venues.length === 0 && <option value="">— create a venue first —</option>}
              {venues.map((v) => (
                <option key={v.id} value={v.id}>{v.name}</option>
              ))}
            </select>
          </label>
          <Button type="submit" disabled={venues.length === 0}>Create event (draft)</Button>
        </form>
      </div>

      <h3>All events</h3>
      <table className="admin-table">
        <thead>
          <tr><th>Title</th><th>Artist</th><th>Date</th><th>Venue</th><th>Status</th><th></th></tr>
        </thead>
        <tbody>
          {events.map((ev) => (
            <tr key={ev.id}>
              <td>{ev.title}</td>
              <td>{ev.artist}</td>
              <td>{formatDateTime(ev.startsAt)}</td>
              <td>{ev.venue.name}</td>
              <td>{ev.status}</td>
              <td>
                {ev.status === 'DRAFT' && (
                  <Button variant="ghost" onClick={() => onPublish(ev.id)}>Publish</Button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
