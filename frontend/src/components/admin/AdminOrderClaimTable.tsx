import { Badge, type BadgeVariant } from '@/components/common/Badge';
import { Button } from '@/components/common/Button';
import type { AdminOrderClaimSummary, OrderClaimStatus } from '@/types/adminOrder';
import {
  getClaimActions,
  getClaimStatusLabel,
  type AdminClaimAction,
} from '@/utils/adminOrderActions';
import { formatServerDateTime } from '@/utils/formatDate';

const CLAIM_STATUS_BADGE: Record<OrderClaimStatus, BadgeVariant> = {
  REQUESTED: 'accent',
  COLLECTING: 'accent',
  DONE: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'neutral',
};

const ACTION_LABEL: Record<AdminClaimAction, string> = {
  approve: '승인',
  collect: '수거',
  complete: '완료',
  reject: '거부',
};

const HEADERS = ['상품주문번호', '주문번호', '회원', '상품', '사유', '상태', '요청일시', '처리'];

interface AdminOrderClaimTableProps {
  claims: AdminOrderClaimSummary[];
  onAction: (claim: AdminOrderClaimSummary, action: AdminClaimAction) => void;
}

export function AdminOrderClaimTable({ claims, onAction }: AdminOrderClaimTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-[960px] w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs text-content-muted">
            {HEADERS.map((header) => (
              <th key={header} scope="col" className="py-2 pr-3 font-medium">
                {header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {claims.map((claim) => (
            <tr key={claim.claimId} className="border-b border-line last:border-0">
              <td className="py-2.5 pr-3 font-medium">{claim.productOrderNumber}</td>
              <td className="py-2.5 pr-3 text-content-muted">{claim.orderNumber}</td>
              <td className="py-2.5 pr-3 text-content-muted">{claim.memberEmail}</td>
              <td className="py-2.5 pr-3">{claim.productName}</td>
              <td className="max-w-56 truncate py-2.5 pr-3 text-content-muted">
                {claim.reason ?? '-'}
              </td>
              <td className="py-2.5 pr-3">
                <Badge variant={CLAIM_STATUS_BADGE[claim.status]} className="whitespace-nowrap">
                  {getClaimStatusLabel(claim.type, claim.status)}
                </Badge>
              </td>
              <td className="py-2.5 pr-3 whitespace-nowrap text-content-muted">
                {formatServerDateTime(claim.requestedAt)}
              </td>
              <td className="py-2.5 pr-3">
                <div className="flex gap-1.5">
                  {getClaimActions(claim).map((action) => (
                    <Button
                      key={action}
                      variant={action === 'reject' ? 'secondary' : 'primary'}
                      size="sm"
                      aria-label={`${claim.productOrderNumber} ${ACTION_LABEL[action]}`}
                      onClick={() => onAction(claim, action)}
                    >
                      {ACTION_LABEL[action]}
                    </Button>
                  ))}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
