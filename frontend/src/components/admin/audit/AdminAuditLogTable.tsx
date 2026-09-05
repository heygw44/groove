import { Link } from 'react-router-dom';

import { Badge } from '@/components/common/Badge';
import { AUDIT_ACTION_LABELS, AUDIT_TARGET_TYPE_LABELS } from '@/constants/adminAudit';
import type { AdminAuditLog } from '@/types/adminAuditLog';
import { formatDateTime } from '@/utils/formatDate';

import { AuditDetailCell } from './AuditDetailCell';

interface AdminAuditLogTableProps {
  logs: AdminAuditLog[];
}

function AuditTargetCell({ log }: { log: AdminAuditLog }) {
  const label = `${AUDIT_TARGET_TYPE_LABELS[log.targetType]} #${log.targetId}`;

  if (log.targetType === 'PRODUCT') {
    return (
      <Link
        to={`/admin/products/${log.targetId}/edit`}
        className="text-content underline-offset-2 hover:underline"
      >
        {label}
      </Link>
    );
  }

  return <span>{label}</span>;
}

export function AdminAuditLogTable({ logs }: AdminAuditLogTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-[900px] w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs text-content-muted">
            <th className="py-2 pr-3 font-medium">시각</th>
            <th className="py-2 pr-3 font-medium">관리자</th>
            <th className="py-2 pr-3 font-medium">행위</th>
            <th className="py-2 pr-3 font-medium">대상</th>
            <th className="py-2 pr-3 font-medium">변경 내용</th>
            <th className="py-2 pr-3 font-medium">IP</th>
          </tr>
        </thead>
        <tbody>
          {logs.map((log) => (
            <tr key={log.id} className="border-b border-line last:border-0">
              <td className="py-2.5 pr-3 whitespace-nowrap text-content-muted">
                {formatDateTime(log.createdAt)}
              </td>
              <td className="py-2.5 pr-3 whitespace-nowrap">
                {log.adminNickname} #{log.adminId}
              </td>
              <td className="py-2.5 pr-3">
                <Badge variant="neutral">{AUDIT_ACTION_LABELS[log.action]}</Badge>
              </td>
              <td className="py-2.5 pr-3 whitespace-nowrap">
                <AuditTargetCell log={log} />
              </td>
              <td className="py-2.5 pr-3">
                <AuditDetailCell detail={log.detail} />
              </td>
              <td className="py-2.5 pr-3 text-content-muted">{log.ipAddress ?? '-'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
