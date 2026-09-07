import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

import { ToastContext, type ToastType } from '@/components/common/toastContext';

interface ToastMessage {
  id: number;
  type: ToastType;
  message: string;
}

// WCAG 2.2.1(Timing Adjustable): 자동 소멸 전에 사용자가 읽을 시간을 충분히 준다.
const TOAST_DURATION_MS = 5000;

// Date.now() 는 같은 밀리초에 두 번 호출되면(StrictMode 이펙트 이중 실행 등) 키가 중복된다.
let nextToastId = 0;

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const dismissToast = useCallback((id: number) => {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  }, []);

  const showToast = useCallback((type: ToastType, message: string) => {
    const id = ++nextToastId;
    setToasts((prev) => [...prev, { id, type, message }]);
  }, []);

  const errorToasts = toasts.filter((toast) => toast.type === 'error');
  const generalToasts = toasts.filter((toast) => toast.type !== 'error');

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      {createPortal(
        /* Modal/Drawer 도 document.body 에 z-50 으로 포털되므로, 열려 있는 동안에도 보이려면 더 높은 z 가 필요하다. */
        <div className="fixed bottom-4 right-4 z-[100] flex flex-col gap-2">
          {/* 라이브 리전은 변경 전에 이미 DOM 에 있어야 스크린리더가 감지한다 — 컨테이너는 토스트가 없어도 항상 마운트한다. */}
          <div role="status" aria-live="polite" className="contents">
            {generalToasts.map((toast) => (
              <ToastItem key={toast.id} toast={toast} onDismiss={dismissToast} />
            ))}
          </div>
          <div role="alert" aria-live="assertive" className="contents">
            {errorToasts.map((toast) => (
              <ToastItem key={toast.id} toast={toast} onDismiss={dismissToast} />
            ))}
          </div>
        </div>,
        document.body,
      )}
    </ToastContext.Provider>
  );
}

function ToastItem({ toast, onDismiss }: { toast: ToastMessage; onDismiss: (id: number) => void }) {
  const timerRef = useRef<ReturnType<typeof setTimeout>>();

  const startTimer = useCallback(() => {
    timerRef.current = setTimeout(() => onDismiss(toast.id), TOAST_DURATION_MS);
  }, [onDismiss, toast.id]);

  const stopTimer = useCallback(() => {
    clearTimeout(timerRef.current);
  }, []);

  useEffect(() => {
    startTimer();
    return stopTimer;
    // 마운트 시 한 번만 시작하고, 이후로는 hover/focus 핸들러가 재시작을 맡는다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div
      tabIndex={0}
      onMouseEnter={stopTimer}
      onMouseLeave={startTimer}
      onFocus={stopTimer}
      onBlur={startTimer}
      className={`max-w-sm break-words rounded-md px-4 py-2 text-sm text-white shadow-lg ${
        toast.type === 'success'
          ? 'bg-success'
          : toast.type === 'error'
            ? 'bg-danger'
            : 'bg-content'
      }`}
    >
      {toast.message}
    </div>
  );
}
