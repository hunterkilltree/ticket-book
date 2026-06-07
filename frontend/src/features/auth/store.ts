import { create } from 'zustand';
import type { User } from '@/types';
import { getToken, setToken } from '@/services/authToken';
import { loginUser, registerUser } from './api';

interface AuthState {
  user: User | null;
  isAuthenticated: boolean;
  login: (email: string, password: string) => Promise<boolean>;
  register: (fullName: string, email: string, password: string) => Promise<boolean>;
  logout: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  user: null,
  isAuthenticated: Boolean(getToken()),
  login: async (email, password) => {
    const res = await loginUser(email, password);
    if (!res.ok) return false;
    setToken(res.data.token);
    set({ user: res.data.user, isAuthenticated: true });
    return true;
  },
  register: async (fullName, email, password) => {
    const res = await registerUser(fullName, email, password);
    if (!res.ok) return false;
    setToken(res.data.token);
    set({ user: res.data.user, isAuthenticated: true });
    return true;
  },
  logout: () => {
    setToken(null);
    set({ user: null, isAuthenticated: false });
  },
}));
