import type { ButtonHTMLAttributes, ReactNode } from 'react';

import {
  BUTTON_BASE_CLASS,
  BUTTON_SIZE_CLASS,
  BUTTON_VARIANT_CLASS,
  type ButtonSize,
  type ButtonVariant,
} from '@/components/common/buttonStyles';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  children: ReactNode;
  variant?: ButtonVariant;
  size?: ButtonSize;
}

export function Button({
  children,
  variant = 'primary',
  size = 'md',
  className = '',
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`${BUTTON_BASE_CLASS} ${BUTTON_VARIANT_CLASS[variant]} ${BUTTON_SIZE_CLASS[size]} ${className}`}
      {...rest}
    >
      {children}
    </button>
  );
}
