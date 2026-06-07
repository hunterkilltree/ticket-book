import { useEffect } from 'react';
import { connectSeatSocket } from '@/services/websocketClient';
import type { SeatStatusUpdate } from '@/types';

/** Subscribe to live seat updates for an event. `onUpdate` should be stable (useCallback). */
export function useSeatWebSocket(eventId: string, onUpdate: (u: SeatStatusUpdate) => void): void {
  useEffect(() => {
    if (!eventId) return;
    const socket = connectSeatSocket(eventId, onUpdate);
    return () => socket.deactivate();
  }, [eventId, onUpdate]);
}
