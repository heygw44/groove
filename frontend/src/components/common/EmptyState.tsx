import type { ReactNode } from 'react';

interface EmptyStateProps {
  title: string;
  description?: string;
  action?: ReactNode;
}

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="rounded-lg border border-dashed border-line-strong px-6 py-12 text-center">
      <p className="text-sm font-medium text-content">{title}</p>
      {description && <p className="mt-1.5 text-sm text-content-muted">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
