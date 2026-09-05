import { useEffect, useId, useState } from 'react';

import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import { Select } from '@/components/common/Select';
import { ArtistSearchSelect } from '@/components/product/ArtistSearchSelect';
import { useGenres, useLabels } from '@/hooks/queries/useReferences';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { hasActiveFilters, type ProductListFilters } from '@/utils/productFilters';

interface ProductFilterPanelProps {
  filters: ProductListFilters;
  onUpdate: (patch: Partial<ProductListFilters>, options?: { replace?: boolean }) => void;
  onReset: () => void;
  artistSelectedName?: string;
}

/*
 * 섹션마다 구분선을 둬 경계를 만든다 - 간격만으로는 다섯 섹션이 한 덩어리로 보인다.
 * fieldset 에 직접 주면 legend 가 선 위에 얹혀 구분선을 끊으므로 감싸는 div 에 준다.
 */
const SECTION_CLASS = 'border-t border-line pt-5';

export function ProductFilterPanel({
  filters,
  onUpdate,
  onReset,
  artistSelectedName,
}: ProductFilterPanelProps) {
  const uid = useId();
  const { data: genres } = useGenres();
  const { data: labels } = useLabels();

  const [keyword, setKeyword] = useState(filters.keyword ?? '');
  const debouncedKeyword = useDebouncedValue(keyword, 300);

  const [minPrice, setMinPrice] = useState(filters.minPrice?.toString() ?? '');
  const [maxPrice, setMaxPrice] = useState(filters.maxPrice?.toString() ?? '');
  const [priceError, setPriceError] = useState<string | undefined>(undefined);

  /*
   * URL 이 외부(뒤로가기 등)에서 바뀌면 입력창도 따라가야 한다. 렌더 중 이전 값과
   * 비교해 setState 하는 건 React 공식 패턴(리다이렉트 없이 effect 를 피함) -
   * effect 안에서 하면 커밋 후 한 프레임 늦게 반영되고 set-state-in-effect 경고도 뜬다.
   */
  const [prevUrlKeyword, setPrevUrlKeyword] = useState(filters.keyword);
  if (filters.keyword !== prevUrlKeyword) {
    setPrevUrlKeyword(filters.keyword);
    setKeyword(filters.keyword ?? '');
  }

  const [prevUrlPrice, setPrevUrlPrice] = useState([filters.minPrice, filters.maxPrice]);
  if (filters.minPrice !== prevUrlPrice[0] || filters.maxPrice !== prevUrlPrice[1]) {
    setPrevUrlPrice([filters.minPrice, filters.maxPrice]);
    setMinPrice(filters.minPrice?.toString() ?? '');
    setMaxPrice(filters.maxPrice?.toString() ?? '');
  }

  /* 디바운스 반영만 replace - 타이핑 한 글자마다 히스토리 스택이 쌓이면 뒤로가기가 무의미해진다. */
  useEffect(() => {
    const trimmed = debouncedKeyword.trim();
    if ((trimmed || undefined) !== filters.keyword) {
      onUpdate({ keyword: trimmed || undefined }, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedKeyword]);

  const applyPriceRange = () => {
    const min = minPrice ? Number(minPrice) : undefined;
    const max = maxPrice ? Number(maxPrice) : undefined;
    if (min !== undefined && max !== undefined && min > max) {
      setPriceError('최소 가격은 최대 가격보다 클 수 없습니다.');
      return;
    }
    setPriceError(undefined);
    onUpdate({ minPrice: min, maxPrice: max });
  };

  return (
    <div className="flex flex-col gap-5">
      <div>
        <label htmlFor={`${uid}-keyword`} className="mb-1.5 block text-sm font-bold">
          검색어
        </label>
        <Input
          id={`${uid}-keyword`}
          placeholder="앨범명, 아티스트명"
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
        />
      </div>

      <div className={SECTION_CLASS}>
        <span className="mb-1.5 block text-sm font-bold">아티스트</span>
        <ArtistSearchSelect
          value={filters.artistId}
          selectedName={artistSelectedName}
          onChange={(artist) => onUpdate({ artistId: artist?.id })}
        />
      </div>

      <div className={SECTION_CLASS}>
        <label htmlFor={`${uid}-label`} className="mb-1.5 block text-sm font-bold">
          레이블
        </label>
        <Select
          id={`${uid}-label`}
          value={filters.labelId ?? ''}
          onChange={(event) =>
            onUpdate({ labelId: event.target.value ? Number(event.target.value) : undefined })
          }
        >
          <option value="">전체</option>
          {labels?.map((label) => (
            <option key={label.id} value={label.id}>
              {label.name}
            </option>
          ))}
        </Select>
      </div>

      <div className={SECTION_CLASS}>
        <fieldset>
          <legend className="mb-1.5 text-sm font-bold">가격</legend>
          <div className="flex items-center gap-2">
            <Input
              type="number"
              inputMode="numeric"
              min={0}
              placeholder="최소"
              value={minPrice}
              onChange={(event) => setMinPrice(event.target.value)}
              aria-label="최소 가격"
            />
            <span aria-hidden className="text-content-subtle">
              ~
            </span>
            <Input
              type="number"
              inputMode="numeric"
              min={0}
              placeholder="최대"
              value={maxPrice}
              onChange={(event) => setMaxPrice(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  applyPriceRange();
                }
              }}
              aria-label="최대 가격"
            />
          </div>
          {priceError && <p className="mt-1.5 text-xs text-danger">{priceError}</p>}
          <Button variant="secondary" size="sm" className="mt-2 w-full" onClick={applyPriceRange}>
            적용
          </Button>
        </fieldset>
      </div>

      <div className={SECTION_CLASS}>
        <fieldset>
          <legend className="mb-1.5 text-sm font-bold">장르</legend>
          {/* 장르가 늘어나도 사이드바가 한없이 길어지지 않게 목록만 스크롤시킨다. */}
          <div className="flex max-h-48 flex-col gap-1.5 overflow-y-auto pr-1">
            <label className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                className="accent-content"
                checked={filters.genreIds === undefined}
                onChange={() => onUpdate({ genreIds: undefined })}
              />
              전체
            </label>
            {genres?.map((genre) => (
              <label key={genre.id} className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  className="accent-content"
                  checked={filters.genreIds?.includes(genre.id) ?? false}
                  onChange={(event) => {
                    const current = filters.genreIds ?? [];
                    const next = event.target.checked
                      ? [...current, genre.id]
                      : current.filter((id) => id !== genre.id);
                    onUpdate({ genreIds: next.length > 0 ? next : undefined });
                  }}
                />
                {genre.name}
              </label>
            ))}
          </div>
        </fieldset>
      </div>

      {hasActiveFilters(filters) && (
        <Button variant="ghost" size="sm" className="w-full" onClick={onReset}>
          필터 초기화
        </Button>
      )}
    </div>
  );
}
