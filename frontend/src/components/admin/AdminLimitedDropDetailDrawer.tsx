import { Link } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { Drawer } from '@/components/common/Drawer';
import { EmptyState } from '@/components/common/EmptyState';
import { Spinner } from '@/components/common/Spinner';
import { DropStatusBadge } from '@/components/limited/DropStatusBadge';
import { OrderStatusBadge } from '@/components/order/OrderStatusBadge';
import { useAdminLimitedDrop } from '@/hooks/queries/useAdminLimitedDrops';
import { formatDateTime } from '@/utils/formatDate';

interface AdminLimitedDropDetailDrawerProps {
  dropId?: number;
  onClose: () => void;
}

export function AdminLimitedDropDetailDrawer({
  dropId,
  onClose,
}: AdminLimitedDropDetailDrawerProps) {
  const { data: detail, isPending, isError, refetch } = useAdminLimitedDrop(dropId);

  const isMismatched =
    detail?.redisRemaining !== undefined && detail.redisRemaining !== detail.dbRemaining;

  return (
    <Drawer
      open={dropId !== undefined}
      onClose={onClose}
      side="right"
      size="lg"
      title={detail?.productTitle ?? '한정반 드롭 상세'}
    >
      {isPending && (
        <div className="flex min-h-48 items-center justify-center">
          <Spinner />
        </div>
      )}

      {!isPending && isError && (
        <EmptyState
          title="드롭 정보를 불러오지 못했습니다."
          description="잠시 후 다시 시도해주세요."
          action={
            <Button variant="secondary" onClick={() => refetch()}>
              다시 시도
            </Button>
          }
        />
      )}

      {!isPending && !isError && detail && (
        <div className="flex flex-col gap-6">
          <div>
            <div className="flex items-center gap-2">
              <DropStatusBadge status={detail.status} />
              <span className="text-xs text-content-muted">
                등록 {formatDateTime(detail.createdAt)}
              </span>
            </div>
            <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
              <dt className="text-content-muted">총 수량</dt>
              <dd>{detail.totalQuantity}</dd>
              <dt className="text-content-muted">판매 수량</dt>
              <dd>{detail.soldCount}</dd>
              <dt className="text-content-muted">1인 한도</dt>
              <dd>{detail.perMemberLimit}</dd>
              <dt className="text-content-muted">오픈 시각</dt>
              <dd>{formatDateTime(detail.openAt)}</dd>
              <dt className="text-content-muted">마감 시각</dt>
              <dd>{formatDateTime(detail.closeAt)}</dd>
            </dl>
          </div>

          <div className="rounded-lg border border-line p-4">
            <p className="mb-2 text-sm font-bold">재고 대조</p>
            <div className="flex items-center gap-6 text-sm">
              <div>
                <p className="text-content-muted">DB 남은 수량</p>
                <p className="text-base font-semibold">{detail.dbRemaining}</p>
              </div>
              <div>
                <p className="text-content-muted">Redis 카운터</p>
                <p className="text-base font-semibold">{detail.redisRemaining ?? '없음'}</p>
              </div>
            </div>
            {detail.redisRemaining === undefined && (
              <p className="mt-2 text-xs text-content-muted">
                카운터 없음 (OPEN 상태가 아니거나 초기화 전)
              </p>
            )}
            {isMismatched && (
              <p className="mt-2 rounded-md bg-danger-soft px-3 py-2 text-xs text-danger">
                불일치 — 강제 오픈으로 카운터를 DB 기준으로 재초기화할 수 있습니다.
              </p>
            )}
          </div>

          <div>
            <p className="mb-2 text-sm font-bold">구매자 목록</p>
            {detail.purchases.length === 0 ? (
              <p className="text-sm text-content-muted">아직 구매자가 없습니다.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="min-w-[420px] w-full text-left text-sm">
                  <thead>
                    <tr className="border-b border-line text-xs text-content-muted">
                      <th className="py-2 pr-3 font-medium">닉네임</th>
                      <th className="py-2 pr-3 font-medium">주문번호</th>
                      <th className="py-2 pr-3 font-medium">주문 상태</th>
                      <th className="py-2 pr-3 font-medium">시각</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.purchases.map((purchase) => (
                      <tr key={purchase.id} className="border-b border-line last:border-0">
                        <td className="py-2.5 pr-3">{purchase.memberNickname}</td>
                        <td className="py-2.5 pr-3">
                          {purchase.orderNumber ? (
                            <Link
                              to={`/admin/orders?keyword=${purchase.orderNumber}`}
                              className="text-accent"
                            >
                              {purchase.orderNumber}
                            </Link>
                          ) : (
                            '-'
                          )}
                        </td>
                        <td className="py-2.5 pr-3">
                          {purchase.orderStatus ? (
                            <OrderStatusBadge status={purchase.orderStatus} />
                          ) : (
                            '-'
                          )}
                        </td>
                        <td className="py-2.5 pr-3 text-content-muted">
                          {formatDateTime(purchase.purchasedAt)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}
    </Drawer>
  );
}
