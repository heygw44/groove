import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { ConfirmDialog } from '@/components/common/ConfirmDialog';
import { useToast } from '@/components/common/Toast';
import { useWithdraw } from '@/hooks/mutations/useMemberMutations';
import { getErrorMessage } from '@/utils/apiError';

export function WithdrawSection() {
  const [isConfirmOpen, setIsConfirmOpen] = useState(false);
  const { showToast } = useToast();
  const withdrawMutation = useWithdraw();

  const handleConfirm = () => {
    /* 성공하면 페이지가 새로 열리므로 토스트를 띄울 자리가 없다. */
    withdrawMutation.mutate(undefined, {
      onError: (error) => {
        setIsConfirmOpen(false);
        showToast('error', getErrorMessage(error));
      },
    });
  };

  return (
    <section className="rounded-lg border border-danger-line bg-danger-soft">
      <div className="px-7 pt-5">
        <h2 className="text-[15px] font-bold text-danger">회원 탈퇴</h2>
      </div>
      <div className="flex flex-col gap-4 px-7 pt-2.5 pb-6 sm:flex-row sm:items-end sm:justify-between">
        <p className="max-w-lg text-sm text-danger/85">
          탈퇴하면 주문 내역과 배송지를 다시 볼 수 없고, 같은 이메일로 다시 가입할 수 없습니다.
        </p>
        <div>
          <Button variant="danger" onClick={() => setIsConfirmOpen(true)}>
            회원 탈퇴
          </Button>
        </div>
      </div>

      <ConfirmDialog
        open={isConfirmOpen}
        onClose={() => setIsConfirmOpen(false)}
        onConfirm={handleConfirm}
        title="정말 탈퇴하시겠어요?"
        description="주문 내역과 배송지를 다시 볼 수 없고, 같은 이메일로 다시 가입할 수 없습니다."
        confirmLabel="탈퇴하기"
        pending={withdrawMutation.isPending}
      />
    </section>
  );
}
