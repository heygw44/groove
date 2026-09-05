import { useEffect, useId, useRef, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

interface DrawerProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  side?: 'left' | 'right';
  size?: 'sm' | 'lg';
}

const SIDE_CLASS = {
  left: 'left-0',
  right: 'right-0',
} as const;

const SIZE_CLASS = {
  sm: 'max-w-xs',
  lg: 'max-w-lg',
} as const;

export function Drawer({
  open,
  onClose,
  title,
  children,
  side = 'left',
  size = 'sm',
}: DrawerProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();

  useEffect(() => {
    if (!open) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
      }
    };

    const { overflow } = document.body.style;
    document.body.style.overflow = 'hidden';
    document.addEventListener('keydown', handleKeyDown);
    panelRef.current?.focus();

    return () => {
      document.body.style.overflow = overflow;
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open, onClose]);

  if (!open) {
    return null;
  }

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex bg-content/45"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={`absolute top-0 h-full w-[85vw] overflow-y-auto bg-surface shadow-2xl outline-none ${SIDE_CLASS[side]} ${SIZE_CLASS[size]}`}
      >
        <div className="flex items-center justify-between gap-4 border-b border-line px-4 py-3.5">
          <h2 id={titleId} className="text-base font-bold tracking-tight">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="닫기"
            className="-mr-1 rounded-md p-1 text-content-subtle hover:bg-surface-muted hover:text-content"
          >
            <svg
              width="18"
              height="18"
              viewBox="0 0 20 20"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.7"
              strokeLinecap="round"
              aria-hidden
            >
              <path d="M5.5 5.5l9 9" />
              <path d="M14.5 5.5l-9 9" />
            </svg>
          </button>
        </div>

        <div className="px-4 py-4">{children}</div>
      </div>
    </div>,
    document.body,
  );
}
