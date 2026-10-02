import { useEffect, useState, type RefObject } from 'react';

import { Button } from '@/components/common/Button';
import type { ProductDetail } from '@/types/product';
import { formatPrice } from '@/utils/formatPrice';

interface MobilePurchaseBarProps {
  product: ProductDetail;
  /** 구매 패널을 감싼 요소. 화면에 보이면 바를 숨기고, 버튼을 누르면 여기로 스크롤한다. */
  panelRef: RefObject<HTMLElement | null>;
}

/** 구매 패널이 화면에 보이는 동안 true. IntersectionObserver 가 없으면 항상 false 로 둔다. */
const useIsPanelVisible = (panelRef: RefObject<HTMLElement | null>) => {
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    const panel = panelRef.current;
    if (!panel || typeof IntersectionObserver === 'undefined') {
      return undefined;
    }
    const observer = new IntersectionObserver(([entry]) => setIsVisible(entry.isIntersecting));
    observer.observe(panel);
    return () => observer.disconnect();
  }, [panelRef]);

  return isVisible;
};

const getLabel = (product: ProductDetail, isSoldOut: boolean) => {
  if (!isSoldOut || product.limitedDrop) {
    return '구매하기';
  }
  return product.wishlisted && product.alertEnabled ? '재입고 알림 신청됨' : '재입고 알림 받기';
};

export function MobilePurchaseBar({ product, panelRef }: MobilePurchaseBarProps) {
  const isPanelVisible = useIsPanelVisible(panelRef);
  const isSoldOut = product.status === 'SOLD_OUT' || product.stockQuantity <= 0;
  const label = getLabel(product, isSoldOut);

  if (isPanelVisible) {
    return null;
  }

  const handleClick = () => {
    const panel = panelRef.current;
    if (!panel) {
      return;
    }
    panel.scrollIntoView({ behavior: 'smooth', block: 'center' });
    panel.focus({ preventScroll: true });
  };

  return (
    <div className="fixed inset-x-0 bottom-0 z-40 flex items-center gap-3 border-t border-line bg-surface px-4 pt-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))] md:hidden">
      <p className="text-lg font-bold tabular-nums">{formatPrice(product.price)}</p>
      <Button className="flex-1" onClick={handleClick}>
        {label}
      </Button>
    </div>
  );
}
