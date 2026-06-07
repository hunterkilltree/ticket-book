import { create } from 'zustand';

interface CheckoutState {
  eventId: string | null;
  seatIds: string[];
  setSelection: (eventId: string, seatIds: string[]) => void;
  clear: () => void;
}

export const useCheckoutStore = create<CheckoutState>((set) => ({
  eventId: null,
  seatIds: [],
  setSelection: (eventId, seatIds) => set({ eventId, seatIds }),
  clear: () => set({ eventId: null, seatIds: [] }),
}));
