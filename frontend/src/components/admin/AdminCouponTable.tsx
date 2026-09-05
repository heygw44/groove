import { AdminCouponStatusBadge } from '@/components/admin/AdminCouponStatusBadge';
import { Button } from '@/components/common/Button';
import { getAdminCouponDisplayStatus } from '@/schemas/adminCoupon';
import type { AdminCouponSummary } from '@/types/coupon';
import { formatCouponDiscount } from '@/utils/coupon';
import { formatDateTime } from '@/utils/formatDate';
import { formatPrice } from '@/utils/formatPrice';

interface AdminCouponTableProps {
  coupons: AdminCouponSummary[];
  onEdit: (coupon: AdminCouponSummary) => void;
  onDisable: (coupon: AdminCouponSummary) => void;
}

export function AdminCouponTable({ coupons, onEdit, onDisable }: AdminCouponTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-[820px] w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs text-content-muted">
            <th className="py-2 pr-3 font-medium">코드</th>
            <th className="py-2 pr-3 font-medium">이름</th>
            <th className="py-2 pr-3 font-medium">할인</th>
            <th className="py-2 pr-3 font-medium">최소 주문</th>
            <th className="py-2 pr-3 font-medium">발급/사용</th>
            <th className="py-2 pr-3 font-medium">만료일</th>
            <th className="py-2 pr-3 font-medium">상태</th>
            <th className="py-2 pr-3 font-medium">액션</th>
          </tr>
        </thead>
        <tbody>
          {coupons.map((coupon) => {
            const displayStatus = getAdminCouponDisplayStatus(coupon);

            return (
              <tr key={coupon.id} className="border-b border-line last:border-0">
                <td className="py-2.5 pr-3 font-mono text-xs">{coupon.code}</td>
                <td className="py-2.5 pr-3 text-content">{coupon.name}</td>
                <td className="py-2.5 pr-3">{formatCouponDiscount(coupon)}</td>
                <td className="py-2.5 pr-3">
                  {coupon.minOrderAmount === 0 ? '-' : formatPrice(coupon.minOrderAmount)}
                </td>
                <td className="py-2.5 pr-3">
                  <p>
                    {coupon.issuedCount} / {coupon.usedCount}
                  </p>
                  {coupon.totalQuantity !== undefined && (
                    <p className="text-xs text-content-muted">한도 {coupon.totalQuantity}</p>
                  )}
                </td>
                <td className="py-2.5 pr-3 text-content-muted">{formatDateTime(coupon.expiresAt)}</td>
                <td className="py-2.5 pr-3">
                  <AdminCouponStatusBadge status={displayStatus} />
                </td>
                <td className="py-2.5 pr-3">
                  <div className="flex items-center gap-1.5">
                    <Button variant="secondary" size="sm" onClick={() => onEdit(coupon)}>
                      수정
                    </Button>
                    {displayStatus !== 'DISABLED' && (
                      <Button variant="danger" size="sm" onClick={() => onDisable(coupon)}>
                        비활성화
                      </Button>
                    )}
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
