import type { ReactNode } from 'react';

export type BadgeVariant = 'neutral' | 'accent' | 'danger' | 'success';

interface BadgeProps {
  variant?: BadgeVariant;
  children: ReactNode;
  className?: string;
}

// 배지 배경(*-soft)이 페이지 배경과 대비가 낮아 윤곽선 없이는 안 보인다. 색상별 테두리는
// 비텍스트 대비 3:1(WCAG 1.4.11) 기준으로 surface/surface-muted 양쪽에서 검산한 값이다.
const VARIANT_CLASS: Record<BadgeVariant, string> = {
  neutral: 'bg-surface-muted text-content-muted border-line-strong',
  accent: 'bg-accent-soft text-accent-hover border-accent/85',
  danger: 'bg-danger-soft text-danger border-danger/70',
  success: 'bg-success-soft text-success border-success/80',
};

export function Badge({ variant = 'neutral', children, className = '' }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium ${VARIANT_CLASS[variant]} ${className}`}
    >
      {children}
    </span>
  );
}
