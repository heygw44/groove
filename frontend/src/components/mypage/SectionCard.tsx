import type { ReactNode } from 'react';

interface SectionCardProps {
  title: string;
  description?: string;
  children: ReactNode;
}

export function SectionCard({ title, description, children }: SectionCardProps) {
  return (
    <section className="rounded-lg border border-line bg-surface">
      <header className="border-b border-line px-7 py-5">
        <h2 className="text-[15px] font-bold tracking-tight">{title}</h2>
        {description && <p className="mt-1.5 text-sm text-content-muted">{description}</p>}
      </header>
      <div className="px-7 py-6">{children}</div>
    </section>
  );
}
