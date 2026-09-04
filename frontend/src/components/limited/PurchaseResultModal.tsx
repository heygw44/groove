import { Link } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';

interface PurchaseResultModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  description: string;
  /** 있으면 '확인' 대신 이 경로로 가는 링크 버튼을 같이 보여준다(예: 이미 구매한 경우 내 주문 보기). */
  linkTo?: string;
  linkLabel?: string;
}

/** 한정반 구매 실패(매진/이미 구매)를 알리는 순수 안내 모달. ConfirmDialog 와 달리 취소/확인 선택이 아니다. */
export function PurchaseResultModal({
  open,
  onClose,
  title,
  description,
  linkTo,
  linkLabel,
}: PurchaseResultModalProps) {
  return (
    <Modal
      open={open}
      onClose={onClose}
      title={title}
      description={description}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            확인
          </Button>
          {linkTo && (
            <Link to={linkTo}>
              <Button>{linkLabel}</Button>
            </Link>
          )}
        </>
      }
    />
  );
}
