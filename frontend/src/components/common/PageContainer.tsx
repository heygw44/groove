import type { ReactNode } from 'react';

interface PageContainerProps {
  size?: 'sm' | 'md' | 'lg' | 'xl';
  children: ReactNode;
}

const WIDTH_CLASS = {
  sm: 'max-w-3xl',
  md: 'max-w-5xl',
  lg: 'max-w-6xl',
  xl: 'max-w-7xl',
} as const;

export function PageContainer({ size = 'lg', children }: PageContainerProps) {
  return <div className={`mx-auto ${WIDTH_CLASS[size]} px-4 py-8`}>{children}</div>;
}
