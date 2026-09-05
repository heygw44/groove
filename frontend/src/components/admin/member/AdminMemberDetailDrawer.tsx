import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { Drawer } from '@/components/common/Drawer';
import { EmptyState } from '@/components/common/EmptyState';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { MEMBER_ROLE_LABELS } from '@/constants/adminAudit';
import { useChangeMemberStatus } from '@/hooks/mutations/useAdminMemberMutations';
import { adminMemberKeys } from '@/hooks/queries/queryKeys';
import { useAdminMember } from '@/hooks/queries/useAdminMembers';
import { useAuthStore } from '@/store/authStore';
import type { AdminMemberChangeableStatus } from '@/types/adminMember';
import { getErrorMessage } from '@/utils/apiError';
import { formatDate, formatDateTime } from '@/utils/formatDate';
import { formatPrice } from '@/utils/formatPrice';

import { MemberStatusBadge } from './MemberStatusBadge';
import { MemberStatusChangeDialog } from './MemberStatusChangeDialog';

interface AdminMemberDetailDrawerProps {
  memberId?: number;
  onClose: () => void;
}

export function AdminMemberDetailDrawer({ memberId, onClose }: AdminMemberDetailDrawerProps) {
  const { showToast } = useToast();
  const queryClient = useQueryClient();
  const me = useAuthStore((s) => s.member);
  const { data: detail, isPending, isError, refetch } = useAdminMember(memberId ?? 0);
  const changeStatusMutation = useChangeMemberStatus();

  const [dialogStatus, setDialogStatus] = useState<AdminMemberChangeableStatus>();

  // memberId 가 바뀌면(다른 회원 선택, 드로어 닫힘) 이전 다이얼로그 상태를 들고 있지 않게 한다.
  const [prevMemberId, setPrevMemberId] = useState(memberId);
  if (memberId !== prevMemberId) {
    setPrevMemberId(memberId);
    setDialogStatus(undefined);
  }

  const canSanction =
    detail !== undefined &&
    detail.role !== 'ADMIN' &&
    detail.id !== me?.id &&
    detail.status !== 'WITHDRAWN';

  const handleConfirm = (reason?: string) => {
    if (!memberId || !dialogStatus) {
      return;
    }
    changeStatusMutation.mutate(
      { memberId, status: dialogStatus, reason },
      {
        onSuccess: () => {
          showToast('success', dialogStatus === 'SUSPENDED' ? '회원을 정지했습니다.' : '정지를 해제했습니다.');
          setDialogStatus(undefined);
        },
        onError: (error) => {
          showToast('error', getErrorMessage(error));
          queryClient.invalidateQueries({ queryKey: adminMemberKeys.detail(memberId) });
        },
      },
    );
  };

  return (
    <Drawer
      open={memberId !== undefined}
      onClose={onClose}
      side="right"
      size="lg"
      title={detail?.email ?? '회원 상세'}
    >
      {isPending && (
        <div className="flex min-h-48 items-center justify-center">
          <Spinner />
        </div>
      )}

      {!isPending && isError && (
        <EmptyState
          title="회원 정보를 불러오지 못했습니다."
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
              <MemberStatusBadge status={detail.status} />
              <span className="text-xs text-content-muted">{MEMBER_ROLE_LABELS[detail.role]}</span>
            </div>
            <p className="mt-2 text-sm font-medium text-content">{detail.nickname}</p>
            <p className="text-xs text-content-muted">
              가입일 {formatDate(detail.createdAt)}
            </p>
          </div>

          <div className="grid grid-cols-3 gap-3 rounded-lg border border-line bg-surface-sunken p-4 text-center">
            <div>
              <p className="text-xs text-content-muted">주문 수</p>
              <p className="mt-1 text-sm font-bold">{detail.orderCount}건</p>
            </div>
            <div>
              <p className="text-xs text-content-muted">총 결제액</p>
              <p className="mt-1 text-sm font-bold">{formatPrice(detail.totalPaymentAmount)}</p>
            </div>
            <div>
              <p className="text-xs text-content-muted">사용 가능 쿠폰</p>
              <p className="mt-1 text-sm font-bold">{detail.usableCouponCount}장</p>
            </div>
          </div>

          <div>
            <p className="mb-2 text-sm font-bold">최근 주문</p>
            {detail.recentOrders.length === 0 ? (
              <p className="text-sm text-content-muted">최근 주문이 없습니다.</p>
            ) : (
              <ul className="flex flex-col gap-2">
                {detail.recentOrders.map((order) => (
                  <li
                    key={order.id}
                    className="flex items-center justify-between rounded-md border border-line px-3 py-2 text-sm"
                  >
                    <div className="min-w-0">
                      <Link
                        to={`/admin/orders?keyword=${order.orderNumber}`}
                        className="font-mono text-xs text-content underline-offset-2 hover:underline"
                      >
                        {order.orderNumber}
                      </Link>
                      <p className="truncate text-content-muted">
                        {order.representativeProductName}
                      </p>
                    </div>
                    <div className="shrink-0 text-right">
                      <p className="font-medium">{formatPrice(order.finalAmount)}</p>
                      <p className="text-xs text-content-muted">
                        {formatDateTime(order.createdAt)}
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {canSanction && (
            <div className="border-t border-line pt-5">
              {detail.status === 'SUSPENDED' ? (
                <Button variant="primary" onClick={() => setDialogStatus('ACTIVE')}>
                  정지 해제
                </Button>
              ) : (
                <Button variant="danger" onClick={() => setDialogStatus('SUSPENDED')}>
                  회원 정지
                </Button>
              )}
            </div>
          )}
        </div>
      )}

      {dialogStatus && (
        <MemberStatusChangeDialog
          open={dialogStatus !== undefined}
          nextStatus={dialogStatus}
          pending={changeStatusMutation.isPending}
          onClose={() => setDialogStatus(undefined)}
          onConfirm={handleConfirm}
        />
      )}
    </Drawer>
  );
}
