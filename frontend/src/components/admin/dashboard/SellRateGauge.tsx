interface SellRateGaugeProps {
  /** 백분율(0~100). */
  rate: number;
}

export function SellRateGauge({ rate }: SellRateGaugeProps) {
  const clampedRate = Math.min(100, Math.max(0, rate));

  return (
    <div className="flex items-center gap-2">
      <div className="h-1.5 w-20 overflow-hidden rounded-full bg-surface-muted">
        <div className="h-full rounded-full bg-accent" style={{ width: `${clampedRate}%` }} />
      </div>
      <span className="text-xs text-content-muted">{rate.toFixed(1)}%</span>
    </div>
  );
}
