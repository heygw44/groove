import { useId, useState } from 'react';

import { Input } from '@/components/common/Input';
import { Spinner } from '@/components/common/Spinner';
import { useArtists } from '@/hooks/queries/useReferences';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import type { Artist } from '@/types/product';

interface ArtistSearchSelectProps {
  value?: number;
  /** `GET /artists/{id}` 조회로 얻은 이름. 방금 고른 아티스트(로컬 state)가 이보다 우선한다. */
  selectedName?: string;
  /**
   * 마운트 시점에만 검색창 초기값으로 쓴다(Discogs 프리필로 이름 검색을 미리 시작해준다).
   * 이후 값이 바뀌어도 반응하지 않으므로, 다시 적용하려면 부모가 `key` 를 바꿔 리마운트시켜야 한다.
   */
  initialKeyword?: string;
  onChange: (artist: Artist | undefined) => void;
  id?: string;
  invalid?: boolean;
  disabled?: boolean;
}

export function ArtistSearchSelect({
  value,
  selectedName,
  initialKeyword,
  onChange,
  id,
  invalid = false,
  disabled = false,
}: ArtistSearchSelectProps) {
  const [pickedArtist, setPickedArtist] = useState<Artist | undefined>(undefined);
  const [keyword, setKeyword] = useState(initialKeyword ?? '');
  const [open, setOpen] = useState(Boolean(initialKeyword));
  const debouncedKeyword = useDebouncedValue(keyword, 300);
  const inputId = useId();

  const { data: artists, isFetching } = useArtists(debouncedKeyword || undefined, open);

  const handleSelect = (artist: Artist) => {
    setPickedArtist(artist);
    setKeyword('');
    setOpen(false);
    onChange(artist);
  };

  const handleClear = () => {
    setPickedArtist(undefined);
    onChange(undefined);
  };

  if (value !== undefined) {
    const displayName = pickedArtist?.name ?? selectedName ?? '이름 확인 중';
    return (
      <span className="inline-flex max-w-full items-center gap-1.5 rounded-full bg-accent-soft py-1 pl-3 pr-1.5 text-sm text-accent-hover">
        <span className="truncate">{displayName}</span>
        <button
          type="button"
          onClick={handleClear}
          disabled={disabled}
          aria-label="아티스트 선택 해제"
          className="rounded-full p-0.5 hover:bg-accent-hover/10 disabled:cursor-not-allowed"
        >
          ×
        </button>
      </span>
    );
  }

  return (
    <div className="relative">
      <Input
        id={id ?? inputId}
        role="combobox"
        aria-expanded={open}
        aria-autocomplete="list"
        invalid={invalid}
        disabled={disabled}
        placeholder="아티스트 검색"
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
      />
      {open && (
        <ul className="absolute z-10 mt-1 max-h-56 w-full overflow-y-auto rounded-md border border-line bg-surface py-1 shadow-lg">
          {isFetching && (
            <li className="flex items-center justify-center px-3 py-3">
              <Spinner size="sm" />
            </li>
          )}
          {!isFetching && artists?.length === 0 && (
            <li className="px-3 py-2.5 text-sm text-content-subtle">검색 결과가 없습니다.</li>
          )}
          {!isFetching &&
            artists?.map((artist) => (
              <li key={artist.id}>
                <button
                  type="button"
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => handleSelect(artist)}
                  className="block w-full px-3 py-2 text-left text-sm hover:bg-surface-muted"
                >
                  {artist.name}
                </button>
              </li>
            ))}
        </ul>
      )}
    </div>
  );
}
