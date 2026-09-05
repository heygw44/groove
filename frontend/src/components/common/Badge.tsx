import type { ReactNode } from 'react';

export type BadgeVariant = 'neutral' | 'accent' | 'danger' | 'success';

interface BadgeProps {
  variant?: BadgeVariant;
  children: ReactNode;
  className?: string;
}

const VARIANT_CLASS: Record<BadgeVariant, string> = {
  neutral: 'bg-surface-muted text-content-muted',
  accent: 'bg-accent-soft text-accent-hover',
  danger: 'bg-danger-soft text-danger',
  success: 'bg-success-soft text-success',
};

export function Badge({ variant = 'neutral', children, className = '' }: BadgeProps) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${VARIANT_CLASS[variant]} ${className}`}
    >
      {children}
    </span>
  );
}
