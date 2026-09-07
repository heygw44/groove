import { Link } from 'react-router-dom';

import {
  RecommendProductGrid,
  RecommendProductGridSkeleton,
} from '@/components/recommend/RecommendProductGrid';
import {
  HOME_RECOMMEND_SECTION_TITLE_SUFFIX,
  HOME_RECOMMEND_SIZE,
} from '@/constants/recommendReasons';
import { useHomeRecommendations } from '@/hooks/queries/useRecommendations';
import { useAuthStore } from '@/store/authStore';

interface DiggingSectionProps {
  className?: string;
}

function TasteOnboardingCard({ className }: DiggingSectionProps) {
  return (
    <section className={className}>
      <div className="rounded-lg border border-line bg-surface-muted px-6 py-8 text-center">
        <p className="text-sm font-medium text-content">취향을 알려주면 추천해드립니다</p>
        <p className="mt-1.5 text-sm text-content-muted">
          좋아하는 장르·아티스트·연대만 고르면 홈에서 취향에 맞는 앨범을 추천해드립니다.
        </p>
        <Link
          to="/mypage/taste"
          className="mt-4 inline-flex h-10 items-center justify-center rounded-md bg-content px-4 text-sm font-medium text-surface hover:bg-content-muted"
        >
          취향 설정하기
        </Link>
      </div>
    </section>
  );
}

function TasteOnboardingBanner() {
  return (
    <div className="mt-3 flex items-center justify-between rounded-lg border border-line bg-surface-muted px-4 py-3">
      <p className="text-sm text-content-muted">지금은 인기 상품입니다. 취향을 알려주세요.</p>
      <Link
        to="/mypage/taste"
        className="shrink-0 text-sm font-medium text-content underline underline-offset-2 hover:text-content-muted"
      >
        취향 설정하기
      </Link>
    </div>
  );
}

export function DiggingSection({ className }: DiggingSectionProps) {
  const isLoggedIn = useAuthStore((s) => Boolean(s.accessToken));
  const member = useAuthStore((s) => s.member);
  const { data, isPending, isError } = useHomeRecommendations();
  const sectionTitle = `${member?.nickname ?? '회원'}${HOME_RECOMMEND_SECTION_TITLE_SUFFIX}`;

  if (!isLoggedIn) {
    return null;
  }

  if (isPending) {
    return (
      <section className={className}>
        <h2 className="text-lg font-bold">{sectionTitle}</h2>
        <RecommendProductGridSkeleton count={HOME_RECOMMEND_SIZE} />
      </section>
    );
  }

  // 홈 첫 화면 품질이 더 중요하므로 실패해도 토스트/에러 카드 없이 조용히 숨긴다.
  if (isError) {
    return null;
  }

  const hasNoRecommendations = data.items.length === 0;

  if (data.profileRequired && hasNoRecommendations) {
    return <TasteOnboardingCard className={className} />;
  }

  if (hasNoRecommendations) {
    return null;
  }

  return (
    <section className={className}>
      <h2 className="text-lg font-bold">{sectionTitle}</h2>
      {data.profileRequired && <TasteOnboardingBanner />}
      <RecommendProductGrid items={data.items} />
    </section>
  );
}
