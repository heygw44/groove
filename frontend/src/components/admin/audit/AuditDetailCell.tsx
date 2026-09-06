const DETAIL_PREVIEW_LENGTH = 40;

interface AuditDetailCellProps {
  /** 상세를 남기지 않는 액션(PRODUCT_CREATE 등)은 서버가 키 자체를 생략한다. */
  detail?: string;
}

export function AuditDetailCell({ detail }: AuditDetailCellProps) {
  if (!detail) {
    return <span className="text-content-subtle">-</span>;
  }

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
