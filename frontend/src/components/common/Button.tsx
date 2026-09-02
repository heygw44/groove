import type { ButtonHTMLAttributes, ReactNode } from 'react';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode;
  variant?: 'primary' | 'secondary';
}

export function Button({ children, variant = 'primary', className = '', ...rest }: ButtonProps) {
  const base =
    'rounded px-4 py-2 text-sm font-medium disabled:cursor-not-allowed disabled:opacity-50';
  const variantClass =
    variant === 'primary'
      ? 'bg-neutral-900 text-white hover:bg-neutral-700'
      : 'bg-neutral-100 text-neutral-900 hover:bg-neutral-200';

  return (
    <button className={`${base} ${variantClass} ${className}`} {...rest}>
      {children}
    </button>
  );
}
