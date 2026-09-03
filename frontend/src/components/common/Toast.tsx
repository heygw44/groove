import { useCallback, useState, type ReactNode } from 'react';

import { ToastContext, type ToastType } from '@/components/common/toastContext';

interface ToastMessage {
  id: number;
  type: ToastType;
  message: string;
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastMessage[]>([]);

  const showToast = useCallback((type: ToastType, message: string) => {
    const id = Date.now();
    setToasts((prev) => [...prev, { id, type, message }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((toast) => toast.id !== id));
    }, 3000);
  }, []);

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
        {toasts.map((toast) => (
          <div
            key={toast.id}
            className={`rounded-md px-4 py-2 text-sm text-white shadow-lg ${
              toast.type === 'success'
                ? 'bg-success'
                : toast.type === 'error'
                  ? 'bg-danger'
                  : 'bg-content'
            }`}
          >
            {toast.message}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
