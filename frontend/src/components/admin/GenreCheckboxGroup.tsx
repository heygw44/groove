import type { Genre } from '@/types/product';

interface GenreCheckboxGroupProps {
  value: number[];
  onChange: (value: number[]) => void;
  genres: Genre[];
  name: string;
}

export function GenreCheckboxGroup({ value, onChange, genres, name }: GenreCheckboxGroupProps) {
  const toggle = (genreId: number) => {
    onChange(
      value.includes(genreId) ? value.filter((id) => id !== genreId) : [...value, genreId],
    );
  };

  return (
    <fieldset>
      <legend className="sr-only">장르</legend>
      <div className="flex flex-wrap gap-x-4 gap-y-2">
        {genres.map((genre) => (
          <label key={genre.id} className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              name={name}
              className="h-4 w-4 accent-content"
              checked={value.includes(genre.id)}
              onChange={() => toggle(genre.id)}
            />
            {genre.name}
          </label>
        ))}
      </div>
    </fieldset>
  );
}
