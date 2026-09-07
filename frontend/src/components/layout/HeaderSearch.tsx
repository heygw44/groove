import { useId, useState, type KeyboardEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { Input } from '@/components/common/Input';
import { Spinner } from '@/components/common/Spinner';
import { useSearchSuggestions } from '@/hooks/queries/useSearchSuggestions';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import type { ArtistSuggestion, ProductSuggestion } from '@/types/search';

const MIN_KEYWORD_LENGTH = 2;

type SuggestionItem =
  { kind: 'product'; data: ProductSuggestion } | { kind: 'artist'; data: ArtistSuggestion };

interface HeaderSearchProps {
  autoFocus?: boolean;
  className?: string;
}

/**
 * 헤더 상시 검색창. URL 의 `?keyword=` 와 동기화하지 않는 런처다 - 여기서 검색을
 * 시작하면 `/products` 로 새로 이동하고, 목록 화면 안에서 키워드를 다듬는 건
 * ProductFilterPanel 의 검색어 입력이 계속 맡는다.
 */
export function HeaderSearch({ autoFocus = false, className = '' }: HeaderSearchProps) {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const listboxId = useId();

  const [keyword, setKeyword] = useState('');
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState<number | null>(null);

  const trimmedKeyword = useDebouncedValue(keyword, 300).trim();
  const suggestionsEnabled = open && trimmedKeyword.length >= MIN_KEYWORD_LENGTH;
  const { data, isFetching } = useSearchSuggestions(trimmedKeyword, suggestionsEnabled);

  const products = data?.products ?? [];
  const artists = data?.artists ?? [];
  const items: SuggestionItem[] = [
    ...products.map((product) => ({ kind: 'product' as const, data: product })),
    ...artists.map((artist) => ({ kind: 'artist' as const, data: artist })),
  ];

  /*
   * Header.tsx 의 드로어 닫기와 같은 패턴 - 렌더 중 비교해 setState 한다.
   * effect 로 하면 라우트가 바뀐 뒤 한 프레임 입력값이 남아 깜빡인다.
   */
  const [prevPathname, setPrevPathname] = useState(pathname);
  if (pathname !== prevPathname) {
    setPrevPathname(pathname);
    setOpen(false);
    setKeyword('');
    setActiveIndex(null);
  }

  const reset = () => {
    setOpen(false);
    setKeyword('');
    setActiveIndex(null);
  };

  const goToProduct = (product: ProductSuggestion) => {
    reset();
    navigate(`/products/${product.id}`);
  };

  const goToArtist = (artist: ArtistSuggestion) => {
    reset();
    navigate(`/products?artistId=${artist.id}`);
  };

  const goToKeywordSearch = () => {
    const trimmed = keyword.trim();
    if (!trimmed) {
      return;
    }
    reset();
    navigate(`/products?keyword=${encodeURIComponent(trimmed)}`);
  };

  const selectItem = (item: SuggestionItem) => {
    if (item.kind === 'product') {
      goToProduct(item.data);
    } else {
      goToArtist(item.data);
    }
  };

  const moveCursor = (direction: 1 | -1) => {
    if (items.length === 0) {
      return;
    }
    setActiveIndex((current) => {
      if (current === null) {
        return direction === 1 ? 0 : items.length - 1;
      }
      return (current + direction + items.length) % items.length;
    });
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      moveCursor(1);
      return;
    }
    if (event.key === 'ArrowUp') {
      event.preventDefault();
      moveCursor(-1);
      return;
    }
    if (event.key === 'Enter') {
      event.preventDefault();
      const active = activeIndex !== null ? items[activeIndex] : undefined;
      if (active) {
        selectItem(active);
      } else {
        goToKeywordSearch();
      }
      return;
    }
    if (event.key === 'Escape' && open) {
      // 드롭다운만 닫는다. 열려 있지 않았다면 상위(모바일 검색 줄)로 그대로 버블시켜 거기서 닫게 한다.
      event.preventDefault();
      event.stopPropagation();
      setOpen(false);
    }
  };

  const activeOptionId = activeIndex !== null ? `${listboxId}-option-${activeIndex}` : undefined;
  const showDropdown = open && trimmedKeyword.length >= MIN_KEYWORD_LENGTH;

  const optionClassName = (active: boolean) =>
    `flex w-full items-center gap-2.5 px-3 py-2 text-left text-sm hover:bg-surface-muted ${
      active ? 'bg-surface-muted' : ''
    }`;

  return (
    <div className={`relative ${className}`}>
      <Input
        autoFocus={autoFocus}
        role="combobox"
        aria-expanded={showDropdown}
        aria-controls={listboxId}
        aria-activedescendant={activeOptionId}
        aria-autocomplete="list"
        placeholder="앨범, 아티스트 검색"
        value={keyword}
        onChange={(event) => {
          setKeyword(event.target.value);
          setActiveIndex(null);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        onKeyDown={handleKeyDown}
      />
      {showDropdown && (
        <ul
          id={listboxId}
          role="listbox"
          aria-label="검색 제안"
          className="absolute z-10 mt-1 max-h-96 w-full overflow-y-auto rounded-md border border-line bg-surface py-1 shadow-lg"
        >
          {isFetching && (
            <li className="flex items-center justify-center px-3 py-3">
              <Spinner size="sm" />
            </li>
          )}
          {!isFetching && items.length === 0 && (
            <li className="px-3 py-2.5 text-sm text-content-subtle">검색 결과가 없습니다.</li>
          )}
          {!isFetching && products.length > 0 && (
            <>
              <li role="presentation" className="px-3 pb-1 pt-2 text-xs text-content-subtle">
                상품
              </li>
              {products.map((product, index) => (
                <li
                  key={`product-${product.id}`}
                  id={`${listboxId}-option-${index}`}
                  role="option"
                  aria-selected={activeIndex === index}
                >
                  <button
                    type="button"
                    onMouseDown={(event) => event.preventDefault()}
                    onClick={() => goToProduct(product)}
                    className={optionClassName(activeIndex === index)}
                  >
                    {product.thumbnailUrl ? (
                      <img
                        src={product.thumbnailUrl}
                        alt=""
                        className="h-10 w-10 shrink-0 rounded object-cover"
                      />
                    ) : (
                      <span className="h-10 w-10 shrink-0 rounded bg-surface-muted" aria-hidden />
                    )}
                    <span className="min-w-0">
                      <span className="block truncate text-content">{product.title}</span>
                      <span className="block truncate text-content-muted">
                        {product.artistName}
                      </span>
                    </span>
                  </button>
                </li>
              ))}
            </>
          )}
          {!isFetching && artists.length > 0 && (
            <>
              <li role="presentation" className="px-3 pb-1 pt-2 text-xs text-content-subtle">
                아티스트
              </li>
              {artists.map((artist, artistIndex) => {
                const index = products.length + artistIndex;
                return (
                  <li
                    key={`artist-${artist.id}`}
                    id={`${listboxId}-option-${index}`}
                    role="option"
                    aria-selected={activeIndex === index}
                  >
                    <button
                      type="button"
                      onMouseDown={(event) => event.preventDefault()}
                      onClick={() => goToArtist(artist)}
                      className={optionClassName(activeIndex === index)}
                    >
                      {artist.name}
                    </button>
                  </li>
                );
              })}
            </>
          )}
        </ul>
      )}
    </div>
  );
}
