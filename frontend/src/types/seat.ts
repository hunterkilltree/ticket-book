export type SeatState = 'AVAILABLE' | 'RESERVED' | 'SOLD';

export interface Seat {
  id: string;
  eventId: string;
  section: string;
  row: string;
  number: string;
  state: SeatState;
}

/** Pushed over WebSocket on every seat state change. */
export interface SeatStatusUpdate {
  seatId: string;
  state: SeatState;
}
