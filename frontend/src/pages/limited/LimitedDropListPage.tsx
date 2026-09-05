import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Spinner } from '@/components/common/Spinner';
import { LimitedDropCard } from '@/components/limited/LimitedDropCard';
import { useLimitedDrops } from '@/hooks/queries/useLimitedDrops';
import { useServerNow } from '@/hooks/useServerNow';
import type { LimitedDropSummary } from '@/types/limitedDrop';
import { getErrorMessage } from '@/utils/apiError';
import { applyServerTime } from '@/utils/serverTime';

type ListTab = 'ongoing' | 'upcoming';

const DEFAULT_TAB: ListTab = 'ongoing';

const TAB_LABEL: Record<ListTab, string> = {
  ongoing: '진행중',
  upcoming: '예정',
};

const EMPTY_MESSAGE: Record<ListTab, string> = {
  ongoing: '진행중인 한정반이 없습니다.',
  upcoming: '예정된 한정반이 없습니다.',
};

const parseTab = (searchParams: URLSearchParams): ListTab =>
  searchParams.get('tab') === 'upcoming' ? 'upcoming' : DEFAULT_TAB;

const isTabMatch = (tab: ListTab, drop: LimitedDropSummary): boolean =>
  tab === 'ongoing'
    ? drop.status === 'OPEN' || drop.status === 'SOLD_OUT'
    : drop.status === 'SCHEDULED';

export default function LimitedDropListPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const tab = parseTab(searchParams);
  const nowMs = useServerNow();

  const { data, isPending, isError, error, refetch } = useLimitedDrops();
  const drops = data?.drops.filter((drop) => isTabMatch(tab, drop)) ?? [];

  // 목록 조회 함수 자체에는 부수효과를 넣지 않고, 응답을 받은 화면에서 서버 시각을 반영한다.
  useEffect(() => {
    if (data?.serverTime) {
      applyServerTime(data.serverTime);
    }
  }, [data?.serverTime]);

  const updateTab = (nextTab: ListTab) => {
    setSearchParams(nextTab === DEFAULT_TAB ? {} : { tab: nextTab });
  };

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <h1 className="text-xl font-bold">한정반 드롭</h1>

      <div role="tablist" aria-label="한정반 상태" className="mt-5 flex gap-1">
        {(Object.keys(TAB_LABEL) as ListTab[]).map((value) => {
          const isSelected = value === tab;
          return (
            <button
              key={value}
              type="button"
              role="tab"
              aria-selected={isSelected}
              onClick={() => updateTab(value)}
              className={`h-9 rounded-full px-4 text-sm whitespace-nowrap ${
                isSelected
                  ? 'bg-content text-surface'
                  : 'text-content-muted hover:bg-surface-muted hover:text-content'
              }`}
            >
              {TAB_LABEL[value]}
            </button>
          );
        })}
      </div>

      <div className="mt-5">
        {isPending && (
          <div className="flex min-h-48 items-center justify-center">
            <Spinner />
          </div>
        )}

        {!isPending && isError && (
          <EmptyState
            title="한정반 목록을 불러오지 못했습니다."
            description={getErrorMessage(error)}
            action={
              <Button variant="secondary" onClick={() => refetch()}>
                다시 시도
              </Button>
            }
          />
        )}

        {!isPending && !isError && drops.length === 0 && (
          <EmptyState title={EMPTY_MESSAGE[tab]} />
        )}

        {!isPending && !isError && drops.length > 0 && (
          <div className="grid grid-cols-2 gap-x-4 gap-y-8 sm:grid-cols-3 lg:grid-cols-4">
            {drops.map((drop) => (
              <LimitedDropCard key={drop.id} drop={drop} nowMs={nowMs} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
