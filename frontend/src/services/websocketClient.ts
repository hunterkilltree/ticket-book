import { Client, type IMessage } from '@stomp/stompjs';
import type { SeatStatusUpdate } from '@/types';

function wsUrl(): string {
  const configured = import.meta.env.VITE_WS_URL || '/ws';
  if (configured.startsWith('ws')) return configured;
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws';
  return `${proto}://${window.location.host}${configured}`;
}

export interface SeatSocket {
  deactivate: () => void;
}

/**
 * Subscribe to live seat updates for an event.
 * Broadcast destination (booking-service): /topic/events/{eventId}/seats
 */
export function connectSeatSocket(
  eventId: string,
  onUpdate: (u: SeatStatusUpdate) => void,
): SeatSocket {
  const client = new Client({
    brokerURL: wsUrl(),
    reconnectDelay: 3000,
    onConnect: () => {
      client.subscribe(`/topic/events/${eventId}/seats`, (msg: IMessage) => {
        try {
          onUpdate(JSON.parse(msg.body) as SeatStatusUpdate);
        } catch {
          /* ignore malformed frames */
        }
      });
    },
  });
  client.activate();
  return { deactivate: () => void client.deactivate() };
}
