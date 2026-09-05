import { useEffect, useState } from 'react';

import { Button } from '@/components/common/Button';
import { Input } from '@/components/common/Input';
import { Select } from '@/components/common/Select';
import { MEMBER_ROLE_LABELS, MEMBER_STATUS_LABELS } from '@/constants/adminAudit';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import type { MemberRole, MemberStatus } from '@/types/member';
import type { AdminMemberFilters } from '@/utils/adminMemberFilters';

interface AdminMemberFilterBarProps {
  filters: AdminMemberFilters;
  onChange: (next: Partial<AdminMemberFilters>, options?: { replace?: boolean }) => void;
}

const MEMBER_STATUSES: MemberStatus[] = ['ACTIVE', 'SUSPENDED', 'WITHDRAWN'];
const MEMBER_ROLES: MemberRole[] = ['USER', 'ADMIN'];

const hasActiveFilter = (filters: AdminMemberFilters): boolean =>
  filters.keyword !== '' || filters.status !== undefined || filters.role !== undefined;

export function AdminMemberFilterBar({ filters, onChange }: AdminMemberFilterBarProps) {
  const [keyword, setKeyword] = useState(filters.keyword);
  const debouncedKeyword = useDebouncedValue(keyword, 300);

  /*
   * URL 이 외부(뒤로가기, 초기화 등)에서 바뀌면 입력창도 따라가야 한다. 렌더 중
   * 이전 값과 비교해 setState 하는 건 effect 없이도 같은 프레임에 반영되고
   * react-hooks/set-state-in-effect 경고도 피한다 (AdminOrderFilterBar 와 동일 패턴).
   */
  const [prevUrlKeyword, setPrevUrlKeyword] = useState(filters.keyword);
  if (filters.keyword !== prevUrlKeyword) {
    setPrevUrlKeyword(filters.keyword);
    setKeyword(filters.keyword);
  }

  /* 디바운스 반영만 replace - 타이핑 한 글자마다 히스토리 스택이 쌓이면 뒤로가기가 무의미해진다. */
  useEffect(() => {
    const trimmed = debouncedKeyword.trim();
    if (trimmed !== filters.keyword) {
      onChange({ keyword: trimmed }, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debouncedKeyword]);

  const handleReset = () => {
    setKeyword('');
    onChange({ keyword: '', status: undefined, role: undefined });
  };

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Select
        aria-label="상태 필터"
        value={filters.status ?? ''}
        onChange={(event) =>
          onChange({ status: (event.target.value || undefined) as MemberStatus | undefined })
        }
        className="w-32"
      >
        <option value="">전체</option>
        {MEMBER_STATUSES.map((status) => (
          <option key={status} value={status}>
            {MEMBER_STATUS_LABELS[status]}
          </option>
        ))}
      </Select>

      <Select
        aria-label="역할 필터"
        value={filters.role ?? ''}
        onChange={(event) =>
          onChange({ role: (event.target.value || undefined) as MemberRole | undefined })
        }
        className="w-32"
      >
        <option value="">전체</option>
        {MEMBER_ROLES.map((role) => (
          <option key={role} value={role}>
            {MEMBER_ROLE_LABELS[role]}
          </option>
        ))}
      </Select>

      <Input
        placeholder="이메일 또는 닉네임"
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
        className="w-56"
      />

      {hasActiveFilter(filters) && (
        <Button variant="ghost" size="sm" onClick={handleReset}>
          초기화
        </Button>
      )}
    </div>
  );
}
