import type { Seat } from '@/types';

/** Group seats by section, then by row, for rendering a seat map. */
export function groupSeats(seats: Seat[]): Record<string, Record<string, Seat[]>> {
  const out: Record<string, Record<string, Seat[]>> = {};
  for (const seat of seats) {
    (out[seat.section] ??= {});
    (out[seat.section][seat.row] ??= []).push(seat);
  }
  for (const section of Object.values(out))
    for (const row of Object.values(section))
      row.sort((a, b) => Number(a.number) - Number(b.number));
  return out;
}
