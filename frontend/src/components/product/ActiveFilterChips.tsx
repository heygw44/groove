import { useGenres, useLabels } from '@/hooks/queries/useReferences';
import { formatPrice } from '@/utils/formatPrice';
import type { ProductListFilters } from '@/utils/productFilters';

interface ActiveFilterChipsProps {
  filters: ProductListFilters;
  artistSelectedName?: string;
  onUpdate: (patch: Partial<ProductListFilters>) => void;
  onClearAll: () => void;
}

interface Chip {
  key: string;
  label: string;
  remove: () => void;
}

const formatPriceRange = (min?: number, max?: number): string => {
  if (min !== undefined && max !== undefined) {
    return `${formatPrice(min)} ~ ${formatPrice(max)}`;
  }
  return min !== undefined ? `${formatPrice(min)} 이상` : `${formatPrice(max as number)} 이하`;
};

export function ActiveFilterChips({
  filters,
  artistSelectedName,
  onUpdate,
  onClearAll,
}: ActiveFilterChipsProps) {
  const { data: genres } = useGenres();
  const { data: labels } = useLabels();

  const chips: Chip[] = [];

  if (filters.keyword) {
    chips.push({
      key: 'keyword',
      label: `검색어: ${filters.keyword}`,
      remove: () => onUpdate({ keyword: undefined }),
    });
  }

  if (filters.artistId !== undefined) {
    chips.push({
      key: 'artist',
      /* 아티스트 이름 조회가 끝나기 전에도 칩 자리는 잡아둔다 - 뒤늦게 나타나면 레이아웃이 튄다. */
      label: artistSelectedName ?? '아티스트',
      remove: () => onUpdate({ artistId: undefined }),
    });
  }

  if (filters.labelId !== undefined) {
    const label = labels?.find((item) => item.id === filters.labelId);
    chips.push({
      key: 'label',
      label: label?.name ?? '레이블',
      remove: () => onUpdate({ labelId: undefined }),
    });
  }

  if (filters.minPrice !== undefined || filters.maxPrice !== undefined) {
    chips.push({
      key: 'price',
      label: formatPriceRange(filters.minPrice, filters.maxPrice),
      remove: () => onUpdate({ minPrice: undefined, maxPrice: undefined }),
    });
  }

  filters.genreIds?.forEach((genreId) => {
    const genre = genres?.find((item) => item.id === genreId);
    chips.push({
      key: `genre-${genreId}`,
      label: genre?.name ?? '장르',
      remove: () => {
        const next = (filters.genreIds ?? []).filter((id) => id !== genreId);
        onUpdate({ genreIds: next.length > 0 ? next : undefined });
      },
    });
  });

  if (chips.length === 0) {
    return null;
  }

  return (
    <div className="mb-4 flex flex-wrap items-center gap-2">
      {chips.map((chip) => (
        <span
          key={chip.key}
          className="inline-flex items-center gap-1 rounded-full border border-line bg-surface py-1 pl-2.5 pr-1.5 text-xs"
        >
          {chip.label}
          <button
            type="button"
            aria-label={`${chip.label} 필터 해제`}
            onClick={chip.remove}
            className="flex size-4 items-center justify-center rounded-full text-content-subtle hover:bg-surface-muted hover:text-content"
          >
            <svg width="10" height="10" viewBox="0 0 10 10" stroke="currentColor" aria-hidden>
              <path d="M1 1 L9 9 M9 1 L1 9" strokeWidth="1.4" strokeLinecap="round" />
            </svg>
          </button>
        </span>
      ))}
      <button
        type="button"
        onClick={onClearAll}
        className="ml-1 text-xs text-content-muted underline underline-offset-2 hover:text-content"
      >
        전체 해제
      </button>
    </div>
  );
}
