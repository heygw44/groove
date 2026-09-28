import { useToast } from '@/components/common/toastContext';
import { getBankName } from '@/constants/banks';
import type { VirtualAccount } from '@/types/payment';
import { formatServerDateTime } from '@/utils/formatDate';
import { formatPrice } from '@/utils/formatPrice';

interface VirtualAccountNoticeProps {
  virtualAccount: VirtualAccount;
  amount: number;
}

/** 가상계좌 입금대기 안내 카드. 주문 상세와 주문완료 화면에서 함께 쓴다. */
export function VirtualAccountNotice({ virtualAccount, amount }: VirtualAccountNoticeProps) {
  const { showToast } = useToast();

  const handleCopyAccountNumber = async () => {
    try {
      await navigator.clipboard.writeText(virtualAccount.accountNumber);
      showToast('success', '계좌번호를 복사했습니다.');
    } catch {
      showToast('error', '복사에 실패했습니다.');
    }
  };

  return (
    <div className="rounded-lg border border-line bg-surface-muted px-5 py-4 text-sm">
      <p className="font-bold text-content">무통장입금 계좌 안내</p>
      <div className="mt-2 flex min-w-0 flex-wrap items-center gap-2">
        <span className="text-content-muted">{getBankName(virtualAccount.bankCode)}</span>
        <span className="truncate font-mono font-medium text-content">
          {virtualAccount.accountNumber}
        </span>
        <button
          type="button"
          onClick={handleCopyAccountNumber}
          className="text-xs text-accent hover:text-accent-hover"
        >
          복사
        </button>
      </div>
      {virtualAccount.customerName && (
        <p className="mt-1 text-content-muted">예금주 {virtualAccount.customerName}</p>
      )}
      <p className="mt-1 text-content-muted">
        입금기한 {formatServerDateTime(virtualAccount.dueDate)}까지
      </p>
      <p className="mt-2 text-base font-bold text-content">{formatPrice(amount)}</p>
    </div>
  );
}
