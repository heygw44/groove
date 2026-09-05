const DETAIL_PREVIEW_LENGTH = 40;

interface AuditDetailCellProps {
  detail: string;
}

export function AuditDetailCell({ detail }: AuditDetailCellProps) {
  if (detail.length <= DETAIL_PREVIEW_LENGTH) {
    return <span>{detail}</span>;
  }

  return (
    <details>
      <summary className="cursor-pointer text-content-muted">
        {detail.slice(0, DETAIL_PREVIEW_LENGTH)}…
      </summary>
      <p className="mt-1 whitespace-pre-wrap">{detail}</p>
    </details>
  );
}
