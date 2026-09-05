import { Spinner } from '@/components/common/Spinner';
import { NicknameForm } from '@/components/mypage/NicknameForm';
import { PasswordChangeForm } from '@/components/mypage/PasswordChangeForm';
import { SectionCard } from '@/components/mypage/SectionCard';
import { WithdrawSection } from '@/components/mypage/WithdrawSection';
import { useMe } from '@/hooks/queries/useMe';
import { formatDate } from '@/utils/formatDate';

export default function MyPage() {
  const { data: member, isPending, isError } = useMe();

  if (isPending) {
    return (
      <div className="flex min-h-64 items-center justify-center">
        <Spinner />
      </div>
    );
  }

  if (isError || !member) {
    return <p className="text-sm text-danger">내 정보를 불러오지 못했습니다.</p>;
  }

  return (
    <>
      <SectionCard title="계정 정보">
        <dl className="flex flex-col gap-3 text-sm">
          <div className="flex items-baseline gap-4">
            <dt className="w-20 shrink-0 text-content-muted">이메일</dt>
            <dd className="m-0">{member.email}</dd>
          </div>
          <div className="flex items-baseline gap-4">
            <dt className="w-20 shrink-0 text-content-muted">가입일</dt>
            <dd className="m-0">{formatDate(member.createdAt)}</dd>
          </div>
        </dl>
      </SectionCard>

      <SectionCard title="닉네임" description="리뷰와 주문 내역에 표시되는 이름입니다.">
        <NicknameForm nickname={member.nickname} />
      </SectionCard>

      <SectionCard title="비밀번호 변경" description="변경 후에도 로그인 상태는 유지됩니다.">
        <PasswordChangeForm />
      </SectionCard>

      <WithdrawSection />
    </>
  );
}
