import { useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';

/** 처리 뒤 현재 페이지가 비었으면 URL 의 page 만 바꿔(0 이면 제거) 히스토리를 남기지 않고 이동한다. */
export function useFallbackPageRedirect(fallbackPage: number | undefined) {
  const [, setSearchParams] = useSearchParams();

  useEffect(() => {
    if (fallbackPage === undefined) {
      return;
    }
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        if (fallbackPage > 0) {
          next.set('page', String(fallbackPage));
        } else {
          next.delete('page');
        }
        return next;
      },
      { replace: true },
    );
  }, [fallbackPage, setSearchParams]);
}
