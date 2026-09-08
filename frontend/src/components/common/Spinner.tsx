type SpinnerSize = 'sm' | 'md' | 'lg';
type SpinnerTone = 'default' | 'current';

interface SpinnerProps {
  size?: SpinnerSize;
  /** 어두운 배경(primary 버튼 등)에서는 글자색을 따라가야 보인다. */
  tone?: SpinnerTone;
  /** 버튼 안처럼 상태를 바깥에서 이미 알리는 자리에서는 접근성 트리에서 뺀다. */
  decorative?: boolean;
  className?: string;
}

const SIZE_CLASS: Record<SpinnerSize, string> = {
  sm: 'h-4 w-4 border-2',
  md: 'h-6 w-6 border-2',
  lg: 'h-9 w-9 border-[3px]',
};

const TONE_CLASS: Record<SpinnerTone, string> = {
  default: 'border-line border-t-content',
  current: 'border-current/30 border-t-current',
};

export function Spinner({
  size = 'md',
  tone = 'default',
  decorative = false,
  className = '',
}: SpinnerProps) {
  const a11y = decorative
    ? ({ 'aria-hidden': true } as const)
    : ({ role: 'status', 'aria-label': '로딩 중' } as const);

  return (
    <div
      {...a11y}
      className={`animate-spin rounded-full ${TONE_CLASS[tone]} ${SIZE_CLASS[size]} ${className}`}
    />
  );
}
