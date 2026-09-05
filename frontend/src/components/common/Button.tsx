import type { ButtonHTMLAttributes, ReactNode } from 'react';

type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';
type ButtonSize = 'sm' | 'md';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode;
  variant?: ButtonVariant;
  size?: ButtonSize;
}

const VARIANT_CLASS: Record<ButtonVariant, string> = {
  primary: 'bg-content text-surface hover:bg-content-muted',
  secondary: 'border border-line-strong bg-surface text-content hover:bg-surface-muted',
  danger: 'bg-danger text-white hover:bg-danger-hover',
  ghost: 'text-content-muted hover:bg-surface-muted hover:text-content',
};

const SIZE_CLASS: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-xs',
  md: 'h-10 px-4 text-sm',
};

export function Button({
  children,
  variant = 'primary',
  size = 'md',
  className = '',
  type = 'button',
  ...rest
}: ButtonProps) {
  const base =
    'inline-flex items-center justify-center gap-1.5 rounded-md font-medium whitespace-nowrap ' +
    'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-content ' +
    'disabled:cursor-not-allowed disabled:opacity-45';

  return (
    <button
      type={type}
      className={`${base} ${VARIANT_CLASS[variant]} ${SIZE_CLASS[size]} ${className}`}
      {...rest}
    >
      {children}
    </button>
  );
}
