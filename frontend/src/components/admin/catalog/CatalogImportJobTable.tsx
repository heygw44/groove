import { CatalogImportJobStatusBadge } from '@/components/admin/catalog/CatalogImportJobStatusBadge';
import { Button } from '@/components/common/Button';
import type { CatalogImportJob } from '@/types/catalog';
import { formatDateTime } from '@/utils/formatDate';

interface CatalogImportJobTableProps {
  items: CatalogImportJob[];
  onRestart: (job: CatalogImportJob) => void;
  disabled?: boolean;
}

const EXIT_MESSAGE_MAX_LENGTH = 60;

const truncateExitMessage = (message?: string): string | undefined => {
  if (!message) {
    return undefined;
  }
  return message.length > EXIT_MESSAGE_MAX_LENGTH
    ? `${message.slice(0, EXIT_MESSAGE_MAX_LENGTH)}…`
    : message;
};

export function CatalogImportJobTable({
  items,
  onRestart,
  disabled = false,
}: CatalogImportJobTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-[960px] w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs text-content-muted">
            <th className="py-2 pr-3 font-medium">잡 실행 ID</th>
            <th className="py-2 pr-3 font-medium">마스터 ID</th>
            <th className="py-2 pr-3 font-medium">상태</th>
            <th className="py-2 pr-3 font-medium">진행</th>
            <th className="py-2 pr-3 font-medium">시작</th>
            <th className="py-2 pr-3 font-medium">종료</th>
            <th className="py-2 pr-3 font-medium">실패 사유</th>
            <th className="py-2 pr-3 font-medium">액션</th>
          </tr>
        </thead>
        <tbody>
          {items.map((job) => (
            <tr key={job.jobExecutionId} className="border-b border-line last:border-0">
              <td className="py-2.5 pr-3 text-content">{job.jobExecutionId}</td>
              <td className="py-2.5 pr-3">{job.discogsMasterId ?? '-'}</td>
              <td className="py-2.5 pr-3">
                <CatalogImportJobStatusBadge status={job.status} />
              </td>
              <td className="py-2.5 pr-3 text-content-muted">
                read {job.readCount} · write {job.writeCount} · skip {job.skipCount} · filter{' '}
                {job.filterCount}
              </td>
              <td className="py-2.5 pr-3 text-content-muted">
                {job.startedAt ? formatDateTime(job.startedAt) : '-'}
              </td>
              <td className="py-2.5 pr-3 text-content-muted">
                {job.endedAt ? formatDateTime(job.endedAt) : '-'}
              </td>
              <td className="py-2.5 pr-3 text-content-muted" title={job.exitMessage}>
                {truncateExitMessage(job.exitMessage) ?? '-'}
              </td>
              <td className="py-2.5 pr-3">
                {job.status === 'FAILED' && (
                  <Button
                    variant="secondary"
                    size="sm"
                    disabled={disabled}
                    onClick={() => onRestart(job)}
                  >
                    재시작
                  </Button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
