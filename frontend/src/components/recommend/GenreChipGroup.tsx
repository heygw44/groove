import type { Genre } from '@/types/product';

interface GenreChipGroupProps {
  value: number[];
  onChange: (value: number[]) => void;
  genres: Genre[];
  max: number;
  invalid?: boolean;
}

export function GenreChipGroup({ value, onChange, genres, max, invalid = false }: GenreChipGroupProps) {
  const reachedMax = value.length >= max;

  const toggle = (genreId: number) => {
    onChange(
      value.includes(genreId) ? value.filter((id) => id !== genreId) : [...value, genreId],
    );
  };

  return (
    <fieldset aria-invalid={invalid || undefined}>
      <legend className="sr-only">장르</legend>
      <p className="mb-1.5 text-xs text-content-muted">
        {value.length}/{max}
      </p>
      <div className="flex flex-wrap gap-2">
        {genres.map((genre) => {
          const isSelected = value.includes(genre.id);
          const isDisabled = !isSelected && reachedMax;
          return (
            <button
              key={genre.id}
              type="button"
              aria-pressed={isSelected}
              disabled={isDisabled}
              onClick={() => toggle(genre.id)}
              className={`h-9 rounded-full px-4 text-sm whitespace-nowrap disabled:cursor-not-allowed disabled:opacity-45 ${
                isSelected
                  ? 'bg-content text-surface'
                  : 'border border-line hover:bg-surface-muted'
              }`}
            >
              {genre.name}
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}
