import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { useAdminIdleLogout } from '@/hooks/useAdminIdleLogout';

/** 관리자 콘솔 전용 유휴 로그아웃 경고. AdminLayout 아래에서 한 번만 렌더한다. */
export function AdminIdleGuard() {
  const { warningOpen, remainingSeconds, extend } = useAdminIdleLogout();

  return (
    <Modal
      open={warningOpen}
      onClose={extend}
      title="곧 자동 로그아웃됩니다"
      description={`${remainingSeconds}초 동안 활동이 없으면 로그아웃됩니다.`}
      size="sm"
      footer={<Button onClick={extend}>계속 사용</Button>}
    />
  );
}
