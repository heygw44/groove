import { Button } from '@/components/common/Button';
import { MEMBER_ROLE_LABELS } from '@/constants/adminAudit';
import type { AdminMemberSummary } from '@/types/adminMember';
import { formatDate } from '@/utils/formatDate';
import { formatPrice } from '@/utils/formatPrice';

import { MemberStatusBadge } from './MemberStatusBadge';

interface AdminMemberTableProps {
  members: AdminMemberSummary[];
  onSelect: (member: AdminMemberSummary) => void;
}

export function AdminMemberTable({ members, onSelect }: AdminMemberTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="min-w-[820px] w-full text-left text-sm">
        <thead>
          <tr className="border-b border-line text-xs text-content-muted">
            <th className="py-2 pr-3 font-medium">회원</th>
            <th className="py-2 pr-3 font-medium">역할</th>
            <th className="py-2 pr-3 font-medium">상태</th>
            <th className="py-2 pr-3 font-medium">가입일</th>
            <th className="py-2 pr-3 font-medium">주문 수</th>
            <th className="py-2 pr-3 font-medium">총 결제액</th>
            <th className="py-2 pr-3 font-medium">상세</th>
          </tr>
        </thead>
        <tbody>
          {members.map((member) => (
            <tr key={member.id} className="border-b border-line last:border-0">
              <td className="py-2.5 pr-3">
                <button
                  type="button"
                  onClick={() => onSelect(member)}
                  className="font-medium text-content underline-offset-2 hover:underline"
                >
                  {member.email}
                </button>
                <p className="text-xs text-content-muted">{member.nickname}</p>
              </td>
              <td className="py-2.5 pr-3 text-content-muted">{MEMBER_ROLE_LABELS[member.role]}</td>
              <td className="py-2.5 pr-3">
                <MemberStatusBadge status={member.status} />
              </td>
              <td className="py-2.5 pr-3 text-content-muted">{formatDate(member.createdAt)}</td>
              <td className="py-2.5 pr-3">{member.orderCount}건</td>
              <td className="py-2.5 pr-3">{formatPrice(member.totalPaymentAmount)}</td>
              <td className="py-2.5 pr-3">
                <Button variant="secondary" size="sm" onClick={() => onSelect(member)}>
                  상세
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
