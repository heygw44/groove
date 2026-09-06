import { useState } from 'react';
import { useLocation } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { Modal } from '@/components/common/Modal';
import { TasteProfileForm } from '@/components/recommend/TasteProfileForm';
import { TASTE_ONBOARDING_DISMISSED_KEY } from '@/constants/taste';
import { useTasteProfile } from '@/hooks/queries/useTasteProfile';
import { useAuthStore } from '@/store/authStore';

/** 프라이빗 모드 등에서 sessionStorage 접근이 던질 수 있어 감싼다. */
const readDismissed = () => {
  try {
    return sessionStorage.getItem(TASTE_ONBOARDING_DISMISSED_KEY) === '1';
  } catch {
    return false;
  }
};

const writeDismissed = () => {
  try {
    sessionStorage.setItem(TASTE_ONBOARDING_DISMISSED_KEY, '1');
  } catch {
    // 저장 실패해도 이번 세션 동안은 state 로 닫힌 채 유지된다.
  }
};

export function TasteOnboardingModal() {
  const accessToken = useAuthStore((state) => state.accessToken);
  const isBootstrapping = useAuthStore((state) => state.isBootstrapping);
  const { pathname } = useLocation();
  const { data: profile } = useTasteProfile();
  const [dismissed, setDismissed] = useState(() => readDismissed());

  const dismiss = () => {
    writeDismissed();
    setDismissed(true);
  };

  const open =
    Boolean(accessToken) &&
    !isBootstrapping &&
    profile === null &&
    !dismissed &&
    pathname !== '/mypage/taste';

  return (
    <Modal
      open={open}
      onClose={dismiss}
      title="어떤 판을 좋아하세요?"
      description="취향을 알려주면 홈에서 판을 골라드려요."
      placement="bottom"
      size="md"
      footer={
        <Button variant="ghost" onClick={dismiss}>
          나중에
        </Button>
      }
    >
      {/* 저장되면 프로필이 non-null 이 되어 스스로 닫힌다. dismiss 를 걸면 같은 탭의 다음 로그인까지 숨겨버린다. */}
      <TasteProfileForm submitLabel="저장하고 추천 받기" />
    </Modal>
  );
}
