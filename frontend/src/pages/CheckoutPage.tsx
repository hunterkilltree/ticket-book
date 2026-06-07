import { Link } from 'react-router-dom';
import { Button } from '@/components/Button';
import { useCheckoutStore } from '@/features/checkout/store';
import { useIdempotentCheckout } from '@/features/checkout/useIdempotentCheckout';
import { formatCurrency } from '@/utils/currency';

const SEAT_PRICE = 50;

export function CheckoutPage() {
  const { eventId, seatIds } = useCheckoutStore();
  const total = seatIds.length * SEAT_PRICE;
  const { status, error, checkout } = useIdempotentCheckout(eventId, seatIds, total);

  if (!eventId || seatIds.length === 0) {
    return (
      <section>
        <h1>Checkout</h1>
        <p>Nothing to check out. <Link to="/events">Browse events</Link> and reserve seats first.</p>
      </section>
    );
  }

  if (status === 'success') {
    return (
      <section>
        <h1>Payment confirmed 🎉</h1>
        <p>Your seats are booked. Tickets have been issued.</p>
        <Link to="/tickets"><Button>View my tickets</Button></Link>
      </section>
    );
  }

  return (
    <section className="auth-form">
      <h1>Checkout</h1>
      <div className="card">
        <p>{seatIds.length} seat(s) × {formatCurrency(SEAT_PRICE)}</p>
        <p><strong>Total: {formatCurrency(total)}</strong></p>
      </div>
      {error && <p className="error-text">Payment failed: {error}</p>}
      <Button onClick={checkout} disabled={status === 'processing'}>
        {status === 'processing' ? 'Processing…' : `Pay ${formatCurrency(total)}`}
      </Button>
      <p className="muted">Demo checkout — no real card is charged.</p>
    </section>
  );
}
