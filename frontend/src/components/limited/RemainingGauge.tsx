interface RemainingGaugeProps {
  remaining: number;
  total: number;
  className?: string;
}

export function RemainingGauge({ remaining, total, className = '' }: RemainingGaugeProps) {
  const ratio = total > 0 ? Math.min(Math.max(remaining / total, 0), 1) : 0;

  return (
    <div className={className}>
      <div className="h-2 w-full overflow-hidden rounded-full bg-surface-muted">
        <div
          className="h-full rounded-full bg-accent transition-[width]"
          style={{ width: `${ratio * 100}%` }}
        />
      </div>
      <p className="mt-1.5 text-xs text-content-muted">
        남은 수량 {remaining} / {total}
      </p>
    </div>
  );
}
