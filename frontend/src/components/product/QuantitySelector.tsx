import type { FocusEvent } from 'react';

import { Input } from '@/components/common/Input';

interface QuantitySelectorProps {
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max: number;
  disabled?: boolean;
  id?: string;
}

const clamp = (value: number, min: number, max: number) =>
  Math.min(Math.max(value, min), max);

export function QuantitySelector({
  value,
  onChange,
  min = 1,
  max,
  disabled = false,
  id,
}: QuantitySelectorProps) {
  const isDisabled = disabled || max < min;

  const handleBlur = (event: FocusEvent<HTMLInputElement>) => {
    const raw = event.target.value;
    if (raw === '') {
      onChange(min);
      return;
    }
    onChange(clamp(Number(raw), min, max));
  };

  return (
    <div className="flex items-center gap-2">
      <button
        type="button"
        aria-label="수량 감소"
        disabled={isDisabled || value <= min}
        onClick={() => onChange(clamp(value - 1, min, max))}
        className="h-10 w-10 rounded-md border border-line-strong text-lg disabled:cursor-not-allowed disabled:opacity-45"
      >
        −
      </button>
      <Input
        id={id}
        type="number"
        inputMode="numeric"
        min={min}
        max={max}
        value={value}
        disabled={isDisabled}
        onChange={(event) => onChange(Number(event.target.value))}
        onBlur={handleBlur}
        className="w-16 text-center"
      />
      <button
        type="button"
        aria-label="수량 증가"
        disabled={isDisabled || value >= max}
        onClick={() => onChange(clamp(value + 1, min, max))}
        className="h-10 w-10 rounded-md border border-line-strong text-lg disabled:cursor-not-allowed disabled:opacity-45"
      >
        +
      </button>
    </div>
  );
}
