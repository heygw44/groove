interface SlicePaginationProps {
  /** 0-base. */
  page: number;
  hasNext: boolean;
  onChange: (page: number) => void;
  disabled?: boolean;
}

/** 전체 건수를 모를 때(건수 조회 실패·대기) 목록의 hasNext 만으로 이전/다음 이동을 제공한다. */
export function SlicePagination({
  page,
  hasNext,
  onChange,
  disabled = false,
}: SlicePaginationProps) {
  if (page === 0 && !hasNext) {
    return null;
  }

  const buttonClass =
    'flex h-9 min-w-9 items-center justify-center rounded-md px-1 text-sm text-content-muted hover:bg-surface-muted hover:text-content disabled:cursor-not-allowed disabled:opacity-40';

  return (
    <nav aria-label="페이지" className="flex items-center justify-center gap-1">
      <button
        type="button"
        className={buttonClass}
        onClick={() => onChange(page - 1)}
        disabled={disabled || page === 0}
        aria-label="이전 페이지"
      >
        ‹
      </button>
      <span aria-current="page" className="px-2 text-sm text-content">
        {page + 1}
      </span>
      <button
        type="button"
        className={buttonClass}
        onClick={() => onChange(page + 1)}
        disabled={disabled || !hasNext}
        aria-label="다음 페이지"
      >
        ›
      </button>
    </nav>
  );
}
