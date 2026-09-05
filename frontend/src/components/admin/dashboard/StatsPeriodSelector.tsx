import { useState } from 'react';

import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import {
  isValidStatsPeriod,
  resolvePresetPeriod,
  type StatsPeriodPreset,
} from '@/utils/adminStatsFilters';
import { getServerNow } from '@/utils/serverTime';

interface StatsPeriodValue {
  from: string;
  to: string;
}

interface StatsPeriodSelectorProps {
  value: StatsPeriodValue;
  onChange: (period: StatsPeriodValue) => void;
}

const PRESET_LABEL: Record<StatsPeriodPreset, string> = {
  '7d': '7일',
  '30d': '30일',
};

const RANGE_ERROR_MESSAGE = '시작일은 종료일보다 늦을 수 없고, 기간은 365일 미만이어야 합니다.';

export function StatsPeriodSelector({ value, onChange }: StatsPeriodSelectorProps) {
  const [error, setError] = useState<string>();

  const handlePresetClick = (preset: StatsPeriodPreset) => {
    setError(undefined);
    onChange(resolvePresetPeriod(preset, getServerNow()));
  };

  const handleFromChange = (from: string) => {
    if (!isValidStatsPeriod(from, value.to)) {
      setError(RANGE_ERROR_MESSAGE);
      return;
    }
    setError(undefined);
    onChange({ from, to: value.to });
  };

  const handleToChange = (to: string) => {
    if (!isValidStatsPeriod(value.from, to)) {
      setError(RANGE_ERROR_MESSAGE);
      return;
    }
    setError(undefined);
    onChange({ from: value.from, to });
  };

  const isActivePreset = (preset: StatsPeriodPreset) => {
    const preview = resolvePresetPeriod(preset, getServerNow());
    return preview.from === value.from && preview.to === value.to;
  };

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2">
        {(Object.keys(PRESET_LABEL) as StatsPeriodPreset[]).map((preset) => (
          <button
            key={preset}
            type="button"
            onClick={() => handlePresetClick(preset)}
            className={`h-8 rounded-md px-3 text-xs font-medium ${
              isActivePreset(preset)
                ? 'bg-accent-soft text-accent-hover'
                : 'border border-line-strong text-content-muted hover:bg-surface-muted'
            }`}
          >
            {PRESET_LABEL[preset]}
          </button>
        ))}

        <div className="flex items-center gap-1.5">
          <Input
            type="date"
            aria-label="시작일"
            value={value.from}
            max={value.to}
            onChange={(e) => handleFromChange(e.target.value)}
            className="h-8 w-36 text-xs"
          />
          <span className="text-content-subtle">~</span>
          <Input
            type="date"
            aria-label="종료일"
            value={value.to}
            min={value.from}
            onChange={(e) => handleToChange(e.target.value)}
            className="h-8 w-36 text-xs"
          />
        </div>
      </div>

      <FormError message={error} />
    </div>
  );
}
