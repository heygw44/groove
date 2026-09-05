interface PaginationProps {
  /** 0-base. */
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  siblingCount?: number;
}

type PageItem = number | 'ellipsis';

/** 1-base 표시값 기준으로 첫/끝 페이지와 현재 페이지 주변만 남기고 나머지는 생략 표시한다. */
const buildPageItems = (current1: number, totalPages: number, siblingCount: number): PageItem[] => {
  const items: PageItem[] = [];
  const start = Math.max(2, current1 - siblingCount);
  const end = Math.min(totalPages - 1, current1 + siblingCount);

  items.push(1);
  // 건너뛰는 페이지가 한 쪽뿐이면 접어도 자리를 아끼지 못하니 그냥 번호로 보여준다.
  if (start === 3) {
    items.push(2);
  } else if (start > 3) {
    items.push('ellipsis');
  }
  for (let page = start; page <= end; page += 1) {
    items.push(page);
  }
  if (end === totalPages - 2) {
    items.push(totalPages - 1);
  } else if (end < totalPages - 2) {
    items.push('ellipsis');
  }
  if (totalPages > 1) {
    items.push(totalPages);
  }

  return items;
};

export function Pagination({ page, totalPages, onChange, siblingCount = 1 }: PaginationProps) {
  if (totalPages <= 1) {
    return null;
  }

  const current1 = page + 1;
  const items = buildPageItems(current1, totalPages, siblingCount);
  const buttonBase =
    'flex h-9 w-9 items-center justify-center rounded-md text-sm disabled:cursor-not-allowed disabled:opacity-40';

  return (
    <nav aria-label="페이지" className="flex items-center justify-center gap-1">
      <button
        type="button"
        className={`${buttonBase} text-content-muted hover:bg-surface-muted hover:text-content`}
        onClick={() => onChange(page - 1)}
        disabled={page === 0}
        aria-label="이전 페이지"
      >
        ‹
      </button>

      {items.map((item, index) =>
        item === 'ellipsis' ? (
          <span
            key={`ellipsis-${index}`}
            aria-hidden
            className="flex h-9 w-9 items-center justify-center text-sm text-content-subtle"
          >
            …
          </span>
        ) : (
          <button
            key={item}
            type="button"
            aria-current={item === current1 ? 'page' : undefined}
            className={`${buttonBase} ${
              item === current1 ? 'bg-content text-surface' : 'text-content hover:bg-surface-muted'
            }`}
            onClick={() => onChange(item - 1)}
          >
            {item}
          </button>
        ),
      )}

      <button
        type="button"
        className={`${buttonBase} text-content-muted hover:bg-surface-muted hover:text-content`}
        onClick={() => onChange(page + 1)}
        disabled={page >= totalPages - 1}
        aria-label="다음 페이지"
      >
        ›
      </button>
    </nav>
  );
}
