import { EmptyState } from '@/components/common/EmptyState';
import { LinkButton } from '@/components/common/LinkButton';
import { PageContainer } from '@/components/common/PageContainer';

export default function NotFoundPage() {
  return (
    <PageContainer>
      <EmptyState
        titleAs="h1"
        title="페이지를 찾을 수 없습니다."
        description="주소를 다시 확인해주세요."
        action={<LinkButton to="/">홈으로 가기</LinkButton>}
      />
    </PageContainer>
  );
}
