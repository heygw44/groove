import { QueryErrorState } from '@/components/common/QueryErrorState';
import { Skeleton } from '@/components/common/Skeleton';
import { TasteProfileForm } from '@/components/recommend/TasteProfileForm';
import { useTasteProfile } from '@/hooks/queries/useTasteProfile';

export default function TastePage() {
  const { data, isPending, isError, error, refetch } = useTasteProfile();

  return (
    <div>
      <h2 className="text-xl font-bold">취향</h2>
      <p className="mt-1.5 text-sm text-content-muted">
        좋아하는 장르·아티스트·연대를 알려주면 홈에서 취향에 맞는 앨범을 추천해드립니다.
      </p>

      <div className="mt-5">
        {isPending && (
          <div className="space-y-3">
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-2/3" />
          </div>
        )}

        {!isPending && isError && (
          <QueryErrorState error={error} onRetry={refetch} title="취향을 불러오지 못했습니다." />
        )}

        {!isPending && !isError && <TasteProfileForm profile={data} />}
      </div>
    </div>
  );
}
