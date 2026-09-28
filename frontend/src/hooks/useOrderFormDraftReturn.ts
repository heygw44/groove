import { useState } from 'react';
import { useNavigate } from 'react-router-dom';

import { loadOrderFormDraft, orderDraftToLocationState } from '@/utils/orderDraft';

/**
 * 결제 실패 후 주문서로 돌아가는 공통 로직. PaymentFailPage 와 PaymentSuccessPage(승인 실패)
 * 가 함께 쓴다. sessionStorage 에 남은 초안이 있으면 그 상품 구성으로 주문서를 다시 연다.
 */
export function useOrderFormDraftReturn() {
  const navigate = useNavigate();
  const [draft] = useState(() => loadOrderFormDraft());

  const backToOrderForm = () => {
    if (!draft) {
      return;
    }
    navigate('/orders/new', { state: orderDraftToLocationState(draft.source) });
  };

  return { draft, backToOrderForm };
}
