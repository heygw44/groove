import type { ReactNode } from 'react';
import { Link, type LinkProps } from 'react-router-dom';

import {
  BUTTON_BASE_CLASS,
  BUTTON_SIZE_CLASS,
  BUTTON_VARIANT_CLASS,
  type ButtonSize,
  type ButtonVariant,
} from '@/components/common/buttonStyles';

interface LinkButtonProps extends LinkProps {
  children: ReactNode;
  variant?: ButtonVariant;
  size?: ButtonSize;
}

/** Button 과 같은 스타일을 쓰는 링크. <a> 안에 <button> 을 중첩시키지 않으려고 분리했다. */
export function LinkButton({
  children,
  variant = 'primary',
  size = 'md',
  className = '',
  ...rest
}: LinkButtonProps) {
  return (
    <Link
      className={`${BUTTON_BASE_CLASS} ${BUTTON_VARIANT_CLASS[variant]} ${BUTTON_SIZE_CLASS[size]} ${className}`}
      {...rest}
    >
      {children}
    </Link>
  );
}
