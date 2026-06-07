import { useRef, useState } from 'react';
import { useAuthStore } from '@/features/auth/store';
import { createOrder, payOrder } from './api';

type Status = 'idle' | 'processing' | 'success' | 'error';

export function useIdempotentCheckout(eventId: string | null, seatIds: string[], amount: number) {
  const user = useAuthStore((s) => s.user);
  const orderKey = useRef(crypto.randomUUID());
  const payKey = useRef(crypto.randomUUID());
  const [status, setStatus] = useState<Status>('idle');
  const [error, setError] = useState<string | null>(null);

  async function checkout() {
    setStatus('processing');
    setError(null);
    const orderRes = await createOrder(seatIds, user?.id, orderKey.current);
    if (!orderRes.ok) {
      setError(orderRes.error.title);
      setStatus('error');
      return;
    }
    const payRes = await payOrder(
      orderRes.data.id,
      seatIds,
      amount,
      eventId ?? undefined,
      user?.id,
      payKey.current,
    );
    if (!payRes.ok) {
      setError(payRes.error.title);
      setStatus('error');
      return;
    }
    setStatus('success');
  }

  return { status, error, checkout };
}
