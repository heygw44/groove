import { splitCountdown } from '@/utils/limitedDrop';

interface CountdownTimerProps {
  targetMs: number;
  nowMs: number;
  className?: string;
}

const UNIT_LABEL = ['일', '시간', '분', '초'] as const;

const pad2 = (value: number): string => String(value).padStart(2, '0');

export function CountdownTimer({ targetMs, nowMs, className = '' }: CountdownTimerProps) {
  const { days, hours, minutes, seconds } = splitCountdown(targetMs - nowMs);
  const values = [days, hours, minutes, seconds];

  return (
    <div className={`flex items-center gap-2 ${className}`}>
      {values.map((value, index) => (
        <div key={UNIT_LABEL[index]} className="flex items-baseline gap-1">
          <span className="min-w-8 rounded-md bg-surface-muted px-2 py-1 text-center font-mono text-lg font-bold tabular-nums">
            {pad2(value)}
          </span>
          <span className="text-xs text-content-muted">{UNIT_LABEL[index]}</span>
        </div>
      ))}
    </div>
  );
}
