import { create } from 'zustand';

export type MemberRole = 'USER' | 'ADMIN';

export interface AuthMember {
  id: number;
  email: string;
  nickname: string;
  role: MemberRole;
}

interface AuthState {
  accessToken: string | null;
  member: AuthMember | null;
  setAuth: (accessToken: string, member: AuthMember) => void;
  setAccessToken: (accessToken: string) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  member: null,
  setAuth: (accessToken, member) => set({ accessToken, member }),
  setAccessToken: (accessToken) => set({ accessToken }),
  clearAuth: () => set({ accessToken: null, member: null }),
}));
