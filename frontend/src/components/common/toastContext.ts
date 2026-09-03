import { createContext, useContext } from 'react';

export type ToastType = 'success' | 'error' | 'info';

export interface ToastContextValue {
  showToast: (type: ToastType, message: string) => void;
}

export const ToastContext = createContext<ToastContextValue | null>(null);

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast는 ToastProvider 내부에서만 사용할 수 있습니다.');
  }
  return ctx;
}
