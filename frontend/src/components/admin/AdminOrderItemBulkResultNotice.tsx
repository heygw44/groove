import { Button } from '@/components/common/Button';
import type { AdminOrderItemBulkResult } from '@/types/adminOrder';

interface AdminOrderItemBulkResultNoticeProps {
  label: string;
  result: AdminOrderItemBulkResult;
  onDismiss: () => void;
}

/** 일괄 처리 결과. 서버는 건별 사유 없이 처리·건너뜀 건수만 내려준다. */
export function AdminOrderItemBulkResultNotice({
  label,
  result,
  onDismiss,
}: AdminOrderItemBulkResultNoticeProps) {
  return (
    <div
      role="status"
      className="flex items-center justify-between gap-4 rounded-md border border-line-strong bg-surface-muted px-4 py-3 text-sm"
    >
      <p>
        <span className="font-medium">{label}</span> · 성공 {result.processed}건
        {result.skipped > 0 && (
          <span className="text-danger">
            {' '}
            · 건너뜀 {result.skipped}건 (상태가 맞지 않거나 진행 중인 클레임이 있는 상품주문)
          </span>
        )}
      </p>
      <Button variant="ghost" size="sm" onClick={onDismiss}>
        닫기
      </Button>
    </div>
  );
}
