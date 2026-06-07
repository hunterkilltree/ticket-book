import { useCallback, useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import type { Seat, SeatStatusUpdate, ProblemDetail } from '@/types';
import { Button } from '@/components/Button';
import { useAuthStore } from '@/features/auth/store';
import { useCheckoutStore } from '@/features/checkout/store';
import { listSeats, reserveSeats, type Reservation } from '@/features/seat-picker/api';
import { SeatMap } from '@/features/seat-picker/SeatMap';
import { useSeatWebSocket } from '@/features/seat-picker/useSeatWebSocket';
import { useReservationCountdown } from '@/features/seat-picker/useReservationCountdown';

function mmss(total: number): string {
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

export function SeatPickerPage() {
  const { eventId = '' } = useParams();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const setSelection = useCheckoutStore((s) => s.setSelection);
  const [seats, setSeats] = useState<Seat[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [error, setError] = useState<ProblemDetail | null>(null);
  const [reservation, setReservation] = useState<Reservation | null>(null);
  const [busy, setBusy] = useState(false);
  const secondsLeft = useReservationCountdown(reservation?.expiresAt ?? null);

  useEffect(() => {
    let active = true;
    listSeats(eventId).then((res) => {
      if (!active) return;
      if (res.ok) setSeats(res.data);
      else setError(res.error);
    });
    return () => {
      active = false;
    };
  }, [eventId]);

  const onUpdate = useCallback((u: SeatStatusUpdate) => {
    setSeats((prev) => prev.map((s) => (s.id === u.seatId ? { ...s, state: u.state } : s)));
    if (u.state !== 'AVAILABLE') {
      setSelected((prev) => {
        if (!prev.has(u.seatId)) return prev;
        const next = new Set(prev);
        next.delete(u.seatId);
        return next;
      });
    }
  }, []);
  useSeatWebSocket(eventId, onUpdate);

  const toggle = useCallback((seatId: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(seatId)) next.delete(seatId);
      else next.add(seatId);
      return next;
    });
  }, []);

  async function onReserve() {
    setBusy(true);
    setError(null);
    const res = await reserveSeats(eventId, [...selected], user?.id);
    setBusy(false);
    if (res.ok) {
      setReservation(res.data);
      setSelected(new Set());
    } else {
      setError(res.error);
    }
  }

  function onProceed() {
    if (!reservation) return;
    setSelection(eventId, reservation.reservedSeatIds);
    navigate('/checkout');
  }

  const reserving = reservation && secondsLeft > 0;

  return (
    <section>
      <p>
        <Link to={`/events/${eventId}`}>← Back to event</Link>
      </p>
      <h1>Pick your seats</h1>

      <div className="seat-legend">
        <span className="seat seat-available" /> Available
        <span className="seat seat-reserved" /> Reserved
        <span className="seat seat-sold" /> Sold
        <span className="seat seat-available seat-selected" /> Selected
      </div>

      {seats.length === 0 && !error && <p>Loading seats…</p>}
      {error && <p className="error-text">Couldn’t load/reserve seats: {error.title}</p>}

      <SeatMap seats={seats} selected={selected} onToggle={toggle} />

      <div className="seat-actions">
        <span>{selected.size} selected</span>
        <Button onClick={onReserve} disabled={busy || selected.size === 0}>
          {busy ? 'Reserving…' : 'Reserve seats'}
        </Button>
      </div>

      {reserving && (
        <div className="card reservation-banner">
          <p>
            Reserved {reservation!.reservedSeatIds.length} seat(s). Held for{' '}
            <strong>{mmss(secondsLeft)}</strong>.
          </p>
          {reservation!.failedSeatIds.length > 0 && (
            <p className="muted">{reservation!.failedSeatIds.length} seat(s) were already taken.</p>
          )}
          <Button onClick={onProceed}>Proceed to checkout</Button>
        </div>
      )}
      {reservation && secondsLeft === 0 && (
        <p className="error-text">Your reservation expired — please select seats again.</p>
      )}
    </section>
  );
}
