import { Link } from 'react-router-dom';

interface StatCardProps {
  label: string;
  value: string;
  hint?: string;
  to?: string;
}

export function StatCard({ label, value, hint, to }: StatCardProps) {
  const content = (
    <>
      <p className="text-sm text-content-muted">{label}</p>
      <p className="mt-2 text-2xl font-bold tracking-tight text-content">{value}</p>
      {hint && <p className="mt-1 text-xs text-content-subtle">{hint}</p>}
    </>
  );

  if (to) {
    return (
      <Link
        to={to}
        className="block rounded-lg border border-line-strong p-5 hover:bg-surface-muted"
      >
        {content}
      </Link>
    );
  }

  return <div className="rounded-lg border border-line-strong p-5">{content}</div>;
}
