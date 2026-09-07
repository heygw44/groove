import { useEffect, useRef, type RefObject } from 'react';

interface UseDialogBehaviorOptions {
  open: boolean;
  onClose: () => void;
  panelRef: RefObject<HTMLDivElement>;
}

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

function getFocusable(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR));
}

/** aria-modal 은 실제 Tab 이동을 막지 않으므로, 패널 경계에서 직접 순환시킨다. */
function trapTab(event: KeyboardEvent, panel: HTMLElement) {
  const focusable = getFocusable(panel);
  if (focusable.length === 0) {
    event.preventDefault();
    return;
  }

  const first = focusable[0];
  const last = focusable[focusable.length - 1];
  const active = document.activeElement;
  const isOnFocusable = active instanceof HTMLElement && focusable.includes(active);

  if (event.shiftKey) {
    if (!isOnFocusable || active === first) {
      event.preventDefault();
      last.focus();
    }
  } else if (!isOnFocusable || active === last) {
    event.preventDefault();
    first.focus();
  }
}

/** Modal/Drawer 가 공유하는 ESC 닫기 · body 스크롤 잠금 · 초기 포커스 · Tab 트랩 · 트리거 포커스 복원. */
export function useDialogBehavior({ open, onClose, panelRef }: UseDialogBehaviorOptions) {
  const previouslyFocusedRef = useRef<HTMLElement | null>(null);

  // onClose 가 매 렌더 재생성돼도 복원 대상은 열릴 때 딱 한 번만 기억해야 하므로 별도 effect 로 둔다.
  useEffect(() => {
    if (!open) {
      return;
    }

    previouslyFocusedRef.current = document.activeElement as HTMLElement | null;

    return () => {
      previouslyFocusedRef.current?.focus();
    };
  }, [open]);

  useEffect(() => {
    if (!open) {
      return;
    }

    /* 뒤 페이지가 같이 스크롤되면 다이얼로그가 떠 있다는 감각이 깨진다. */
    const { overflow } = document.body.style;
    document.body.style.overflow = 'hidden';
    panelRef.current?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onClose();
        return;
      }
      if (event.key === 'Tab' && panelRef.current) {
        trapTab(event, panelRef.current);
      }
    };

    document.addEventListener('keydown', handleKeyDown);

    return () => {
      document.body.style.overflow = overflow;
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [open, onClose, panelRef]);
}
