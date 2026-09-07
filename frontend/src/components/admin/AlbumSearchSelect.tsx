import { useId, useState } from 'react';

import { Input } from '@/components/common/Input';
import { Spinner } from '@/components/common/Spinner';
import { useAdminAlbums } from '@/hooks/queries/useAdminAlbums';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import type { AdminAlbumSummary } from '@/types/product';

interface AlbumSearchSelectProps {
  value?: number;
  /** `GET /admin/albums/{id}` 류 조회로 얻은 제목. 방금 고른 앨범(로컬 state)이 이보다 우선한다. */
  selectedTitle?: string;
  onChange: (album: AdminAlbumSummary | undefined) => void;
  id?: string;
  invalid?: boolean;
  disabled?: boolean;
}

const formatAlbumLabel = (
  album: Pick<AdminAlbumSummary, 'title' | 'artistName' | 'originalReleaseYear'>,
) =>
  `${album.title} — ${album.artistName}${album.originalReleaseYear ? ` (${album.originalReleaseYear})` : ''}`;

export function AlbumSearchSelect({
  value,
  selectedTitle,
  onChange,
  id,
  invalid = false,
  disabled = false,
}: AlbumSearchSelectProps) {
  const [pickedAlbum, setPickedAlbum] = useState<AdminAlbumSummary | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  const [open, setOpen] = useState(false);
  const debouncedKeyword = useDebouncedValue(keyword, 300);
  const inputId = useId();

  const { data, isFetching } = useAdminAlbums({ keyword: debouncedKeyword || undefined, size: 10 });
  const albums = data?.content ?? [];

  const handleSelect = (album: AdminAlbumSummary) => {
    setPickedAlbum(album);
    setKeyword('');
    setOpen(false);
    onChange(album);
  };

  const handleClear = () => {
    setPickedAlbum(undefined);
    onChange(undefined);
  };

  if (value !== undefined) {
    const displayName = pickedAlbum
      ? formatAlbumLabel(pickedAlbum)
      : (selectedTitle ?? '이름 확인 중');
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-accent-soft py-1 pl-3 pr-1.5 text-sm text-accent-hover">
        {displayName}
        <button
          type="button"
          onClick={handleClear}
          disabled={disabled}
          aria-label="앨범 선택 해제"
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
        placeholder="앨범 검색"
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
          {!isFetching && albums.length === 0 && (
            <li className="px-3 py-2.5 text-sm text-content-subtle">검색 결과가 없습니다.</li>
          )}
          {!isFetching &&
            albums.map((album) => (
              <li key={album.id}>
                <button
                  type="button"
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => handleSelect(album)}
                  className="block w-full px-3 py-2 text-left text-sm hover:bg-surface-muted"
                >
                  {formatAlbumLabel(album)}
                </button>
              </li>
            ))}
        </ul>
      )}
    </div>
  );
}
