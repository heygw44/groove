import { create } from 'zustand';

import type { Member } from '@/types/member';

interface AuthState {
  accessToken: string | null;
  member: Member | null;
  /** 앱 부팅 시 재발급으로 세션을 복구하는 중인지. 이게 true 인 동안은 아직 모른다. */
  isBootstrapping: boolean;
  setAuth: (accessToken: string, member: Member) => void;
  setAccessToken: (accessToken: string) => void;
  setMember: (member: Member) => void;
  setBootstrapped: () => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  member: null,
  isBootstrapping: true,
  setAuth: (accessToken, member) => set({ accessToken, member }),
  setAccessToken: (accessToken) => set({ accessToken }),
  setMember: (member) => set({ member }),
  setBootstrapped: () => set({ isBootstrapping: false }),
  clearAuth: () => set({ accessToken: null, member: null }),
}));
