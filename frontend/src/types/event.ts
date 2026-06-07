export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'COMPLETED';

export interface Venue {
  id: string;
  name: string;
  address: string;
  capacity: number;
}

export interface Event {
  id: string;
  title: string;
  artist: string;
  startsAt: string; // ISO-8601 instant
  status: EventStatus;
  venue: Venue;
}
