import { useId, useState } from 'react';

import { Input } from '@/components/common/Input';
import { Spinner } from '@/components/common/Spinner';
import { useArtists } from '@/hooks/queries/useReferences';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import type { Artist } from '@/types/product';

interface ArtistChipPickerProps {
  value: Artist[];
  onChange: (value: Artist[]) => void;
  max: number;
  invalid?: boolean;
  id?: string;
}

export function ArtistChipPicker({ value, onChange, max, invalid = false, id }: ArtistChipPickerProps) {
  const [keyword, setKeyword] = useState('');
  const [open, setOpen] = useState(false);
  const debouncedKeyword = useDebouncedValue(keyword, 300);
  const inputId = useId();
  const reachedMax = value.length >= max;

  const { data: artists, isFetching } = useArtists(debouncedKeyword || undefined, open);
  const selectedIds = new Set(value.map((artist) => artist.id));
  const candidates = artists?.filter((artist) => !selectedIds.has(artist.id)) ?? [];

  const handleSelect = (artist: Artist) => {
    onChange([...value, artist]);
    setKeyword('');
    setOpen(false);
  };

  const handleRemove = (artistId: number) => {
    onChange(value.filter((artist) => artist.id !== artistId));
  };

  return (
    <div>
      <p className="mb-1.5 text-xs text-content-muted">
        {value.length}/{max}
      </p>
      {value.length > 0 && (
        <div className="mb-2 flex flex-wrap gap-1.5">
          {value.map((artist) => (
            <span
              key={artist.id}
              className="inline-flex items-center gap-1.5 rounded-full bg-accent-soft py-1 pl-3 pr-1.5 text-sm text-accent-hover"
            >
              {artist.name}
              <button
                type="button"
                onClick={() => handleRemove(artist.id)}
                aria-label={`${artist.name} 해제`}
                className="rounded-full p-0.5 hover:bg-accent-hover/10"
              >
                ×
              </button>
            </span>
          ))}
        </div>
      )}
      <div className="relative">
        <Input
          id={id ?? inputId}
          role="combobox"
          aria-expanded={open}
          aria-autocomplete="list"
          invalid={invalid}
          disabled={reachedMax}
          placeholder={reachedMax ? `최대 ${max}명까지 고를 수 있어요` : '아티스트 검색'}
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          onFocus={() => setOpen(true)}
          onBlur={() => setTimeout(() => setOpen(false), 150)}
        />
        {open && !reachedMax && (
          <ul className="absolute z-10 mt-1 max-h-56 w-full overflow-y-auto rounded-md border border-line bg-surface py-1 shadow-lg">
            {isFetching && (
              <li className="flex items-center justify-center px-3 py-3">
                <Spinner size="sm" />
              </li>
            )}
            {!isFetching && candidates.length === 0 && (
              <li className="px-3 py-2.5 text-sm text-content-subtle">검색 결과가 없습니다.</li>
            )}
            {!isFetching &&
              candidates.map((artist) => (
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
    </div>
  );
}
