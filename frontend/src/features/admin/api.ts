import { http } from '@/services/apiClient';
import type { ApiResult, Event, Venue } from '@/types';

export interface NewVenue {
  name: string;
  address: string;
  capacity: number;
}

export interface NewEvent {
  title: string;
  artist: string;
  startsAt: string; // ISO-8601
  venueId: string;
  status?: string;
}

export function listAllEvents(): Promise<ApiResult<Event[]>> {
  return http.get<Event[]>('/api/events', { all: true });
}

export function createEvent(body: NewEvent): Promise<ApiResult<Event>> {
  return http.post<Event>('/api/events', body);
}

export function publishEvent(id: string): Promise<ApiResult<Event>> {
  return http.put<Event>(`/api/events/${id}`, { status: 'PUBLISHED' });
}

export function listVenues(): Promise<ApiResult<Venue[]>> {
  return http.get<Venue[]>('/api/venues');
}

export function createVenue(body: NewVenue): Promise<ApiResult<Venue>> {
  return http.post<Venue>('/api/venues', body);
}
