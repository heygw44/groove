import axios from 'axios';
import { useEffect, useRef } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Spinner } from '@/components/common/Spinner';
import { CountdownTimer } from '@/components/limited/CountdownTimer';
import { DropStatusBadge } from '@/components/limited/DropStatusBadge';
import { RemainingGauge } from '@/components/limited/RemainingGauge';
import { useLimitedDrop } from '@/hooks/queries/useLimitedDrop';
import { useServerNow } from '@/hooks/useServerNow';
import NotFoundPage from '@/pages/NotFoundPage';
import { useAuthStore } from '@/store/authStore';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';
import { formatDateTime } from '@/utils/formatDate';
import { formatPrice } from '@/utils/formatPrice';
import { getDropPhase, getPurchaseButtonState } from '@/utils/limitedDrop';
import { applyServerTime, toServerMs } from '@/utils/serverTime';

const NOT_FOUND_CODES = new Set(['LIMITED_DROP_NOT_FOUND']);

const ID_PATTERN = /^\d+$/;

// 스케줄러가 openAt 을 지나 OPEN 으로 전이할 때까지, 짧은 간격으로 상태를 다시 확인한다.
const OPENING_POLL_MS = 3_000;

export default function LimitedDropDetailPage() {
  const { id: idParam } = useParams();
  const isValidId = idParam !== undefined && ID_PATTERN.test(idParam);
  const id = isValidId ? Number(idParam) : -1;

  const navigate = useNavigate();
  const location = useLocation();
  const isLoggedIn = useAuthStore((s) => Boolean(s.accessToken));
  const nowMs = useServerNow();
  const { data: drop, isPending, isError, error, refetch } = useLimitedDrop(id);

  const phase = drop ? getDropPhase(drop, nowMs) : undefined;
  const previousPhaseRef = useRef(phase);

  useEffect(() => {
    if (drop) {
      document.title = `${drop.product.title} 한정반 | GROOVE`;
    }
    return () => {
      document.title = 'GROOVE';
    };
  }, [drop]);

  useEffect(() => {
    if (drop?.serverTime) {
      applyServerTime(drop.serverTime);
    }
  }, [drop?.serverTime]);

  // SCHEDULED 를 벗어나는(OPENING/OPEN 진입) 순간 한 번 다시 조회해 최신 상태를 반영한다.
  useEffect(() => {
    if (previousPhaseRef.current === 'SCHEDULED' && phase !== 'SCHEDULED') {
      void refetch();
    }
    previousPhaseRef.current = phase;
  }, [phase, refetch]);

  useEffect(() => {
    if (phase !== 'OPENING') {
      return undefined;
    }
    const interval = setInterval(() => void refetch(), OPENING_POLL_MS);
    return () => clearInterval(interval);
  }, [phase, refetch]);

  // enabled:false 여도 isPending 은 true 이므로, 잘못된 id 분기를 로딩 분기보다 먼저 둔다.
  if (!isValidId) {
    return <NotFoundPage />;
  }

  if (isPending) {
    return (
      <div className="flex min-h-64 items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  const isNotFoundStatus = axios.isAxiosError(error) && error.response?.status === 404;
  if (isError && (isNotFoundStatus || NOT_FOUND_CODES.has(getErrorCode(error) ?? ''))) {
    return <NotFoundPage />;
  }

  if (isError || !drop || !phase) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-8">
        <EmptyState
          title="한정반 정보를 불러오지 못했습니다."
          description={getErrorMessage(error)}
          action={
            <Button variant="secondary" onClick={() => refetch()}>
              다시 시도
            </Button>
          }
        />
      </div>
    );
  }

  const buttonState = getPurchaseButtonState(drop, phase, isLoggedIn);

  const handlePurchaseClick = () => {
    if (!isLoggedIn) {
      const redirect = encodeURIComponent(`${location.pathname}${location.search}`);
      navigate(`/login?redirect=${redirect}`);
      return;
    }
    // TODO(#130): 배송지 선택 시트를 열고 실제 구매 요청을 보낸다.
  };

  return (
    <div className="mx-auto max-w-3xl px-4 py-8">
      <Link to="/limited-drops" className="text-sm text-content-muted">
        ← 한정반 목록
      </Link>

      <div className="mt-4 flex items-center gap-3">
        <DropStatusBadge status={drop.status} />
        <h1 className="text-2xl font-bold tracking-tight">{drop.product.title}</h1>
      </div>
      <p className="mt-1 text-sm text-content-muted">{drop.product.artistName}</p>
      <p className="mt-3 text-xl font-bold">{formatPrice(drop.product.price)}</p>

      {(phase === 'SCHEDULED' || phase === 'OPENING') && (
        <div className="mt-6">
          <p className="mb-2 text-sm text-content-muted">오픈까지</p>
          <CountdownTimer targetMs={toServerMs(drop.openAt)} nowMs={nowMs} />
        </div>
      )}

      <div className="mt-6">
        <RemainingGauge remaining={drop.remainingQuantity} total={drop.totalQuantity} />
      </div>

      <dl className="mt-6 flex flex-col gap-2 text-sm">
        <div className="flex gap-2">
          <dt className="w-24 shrink-0 text-content-muted">1인 구매 한도</dt>
          <dd className="m-0">{drop.perMemberLimit}개</dd>
        </div>
        <div className="flex gap-2">
          <dt className="w-24 shrink-0 text-content-muted">오픈</dt>
          <dd className="m-0">{formatDateTime(drop.openAt)}</dd>
        </div>
        <div className="flex gap-2">
          <dt className="w-24 shrink-0 text-content-muted">마감</dt>
          <dd className="m-0">{formatDateTime(drop.closeAt)}</dd>
        </div>
      </dl>

      <Button
        className="mt-8 w-full"
        disabled={buttonState.disabled}
        onClick={handlePurchaseClick}
      >
        {buttonState.label}
      </Button>
    </div>
  );
}
