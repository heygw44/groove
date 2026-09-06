import { DECADES, DECADE_LABELS } from '@/constants/taste';
import type { Decade } from '@/types/recommend';

interface DecadeToggleGroupProps {
  value: Decade[];
  onChange: (value: Decade[]) => void;
  max: number;
  invalid?: boolean;
}

export function DecadeToggleGroup({ value, onChange, max, invalid = false }: DecadeToggleGroupProps) {
  const reachedMax = value.length >= max;

  const toggle = (decade: Decade) => {
    onChange(value.includes(decade) ? value.filter((item) => item !== decade) : [...value, decade]);
  };

  return (
    <fieldset aria-invalid={invalid || undefined}>
      <legend className="sr-only">연대</legend>
      <p className="mb-1.5 text-xs text-content-muted">
        {value.length}/{max}
      </p>
      <div className="flex flex-wrap gap-2">
        {DECADES.map((decade) => {
          const isSelected = value.includes(decade);
          const isDisabled = !isSelected && reachedMax;
          return (
            <button
              key={decade}
              type="button"
              aria-pressed={isSelected}
              disabled={isDisabled}
              onClick={() => toggle(decade)}
              className={`h-9 rounded-full px-4 text-sm whitespace-nowrap disabled:cursor-not-allowed disabled:opacity-45 ${
                isSelected
                  ? 'bg-content text-surface'
                  : 'border border-line hover:bg-surface-muted'
              }`}
            >
              {DECADE_LABELS[decade]}
            </button>
          );
        })}
      </div>
    </fieldset>
  );
}
