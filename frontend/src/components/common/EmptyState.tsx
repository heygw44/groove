import type { ReactNode } from 'react';

interface EmptyStateProps {
  title: string;
  description?: string;
  action?: ReactNode;
  /** 화면의 유일한 제목 역할을 할 때(예: 결제 결과 페이지) 'h1'/'h2' 로 승격한다. */
  titleAs?: 'p' | 'h1' | 'h2';
}

export function EmptyState({ title, description, action, titleAs = 'p' }: EmptyStateProps) {
  const TitleTag = titleAs;

  return (
    <div className="rounded-lg border border-dashed border-line-strong px-6 py-12 text-center">
      <TitleTag className="text-sm font-medium text-content">{title}</TitleTag>
      {description && <p className="mt-1.5 text-sm text-content-muted">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
