import { create } from 'zustand';

import type { Member } from '@/types/member';

interface AuthState {
  accessToken: string | null;
  member: Member | null;
  setAuth: (accessToken: string, member: Member) => void;
  setAccessToken: (accessToken: string) => void;
  setMember: (member: Member) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  member: null,
  setAuth: (accessToken, member) => set({ accessToken, member }),
  setAccessToken: (accessToken) => set({ accessToken }),
  setMember: (member) => set({ member }),
  clearAuth: () => set({ accessToken: null, member: null }),
}));
