import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { Select } from '@/components/common/Select';
import { AUDIT_ACTION_LABELS, AUDIT_TARGET_TYPE_LABELS } from '@/constants/adminAudit';
import type { AdminAuditAction, AdminAuditTargetType } from '@/types/adminAuditLog';
import type { AdminAuditLogFilters } from '@/utils/adminAuditLogFilters';

interface AdminAuditLogFilterBarProps {
  filters: AdminAuditLogFilters;
  onChange: (next: Partial<AdminAuditLogFilters>, options?: { replace?: boolean }) => void;
}

const AUDIT_ACTIONS = Object.keys(AUDIT_ACTION_LABELS) as AdminAuditAction[];
const AUDIT_TARGET_TYPES = Object.keys(AUDIT_TARGET_TYPE_LABELS) as AdminAuditTargetType[];

const hasActiveFilter = (filters: AdminAuditLogFilters): boolean =>
  filters.action !== undefined ||
  filters.targetType !== undefined ||
  filters.adminId !== undefined ||
  filters.from !== undefined ||
  filters.to !== undefined;

const parseAdminIdInput = (value: string): number | undefined => {
  if (!/^\d+$/.test(value) || Number(value) < 1) {
    return undefined;
  }
  return Number(value);
};

export function AdminAuditLogFilterBar({ filters, onChange }: AdminAuditLogFilterBarProps) {
  /*
   * from/to 는 잘못된 범위를 URL 로 내보내면 파서가 곧바로 둘 다 지워버려
   * 사용자가 방금 고른 값을 잃는다. 로컬 상태로 들고 있다가 유효할 때만
   * onChange 로 반영하고, 무효한 동안은 입력값을 유지한 채 에러만 보여준다.
   */
  const [localFrom, setLocalFrom] = useState(filters.from ?? '');
  const [localTo, setLocalTo] = useState(filters.to ?? '');

  const [prevFrom, setPrevFrom] = useState(filters.from);
  if (filters.from !== prevFrom) {
    setPrevFrom(filters.from);
    setLocalFrom(filters.from ?? '');
  }
  const [prevTo, setPrevTo] = useState(filters.to);
  if (filters.to !== prevTo) {
    setPrevTo(filters.to);
    setLocalTo(filters.to ?? '');
  }

  const isInvalidRange = localFrom !== '' && localTo !== '' && localFrom > localTo;

  const handleFromChange = (value: string) => {
    setLocalFrom(value);
    if (!(value !== '' && localTo !== '' && value > localTo)) {
      onChange({ from: value || undefined });
    }
  };

  const handleToChange = (value: string) => {
    setLocalTo(value);
    if (!(localFrom !== '' && value !== '' && localFrom > value)) {
      onChange({ to: value || undefined });
    }
  };

  const handleReset = () => {
    setLocalFrom('');
    setLocalTo('');
    onChange({
      action: undefined,
      targetType: undefined,
      adminId: undefined,
      from: undefined,
      to: undefined,
    });
  };

  return (
    <div>
      <div className="flex flex-wrap items-center gap-2">
        <Select
          aria-label="행위 필터"
          value={filters.action ?? ''}
          onChange={(event) =>
            onChange({ action: (event.target.value || undefined) as AdminAuditAction | undefined })
          }
          className="w-36"
        >
          <option value="">전체 행위</option>
          {AUDIT_ACTIONS.map((action) => (
            <option key={action} value={action}>
              {AUDIT_ACTION_LABELS[action]}
            </option>
          ))}
        </Select>

        <Select
          aria-label="대상 필터"
          value={filters.targetType ?? ''}
          onChange={(event) =>
            onChange({
              targetType: (event.target.value || undefined) as AdminAuditTargetType | undefined,
            })
          }
          className="w-32"
        >
          <option value="">전체 대상</option>
          {AUDIT_TARGET_TYPES.map((targetType) => (
            <option key={targetType} value={targetType}>
              {AUDIT_TARGET_TYPE_LABELS[targetType]}
            </option>
          ))}
        </Select>

        <Input
          type="number"
          min={1}
          aria-label="관리자 ID"
          placeholder="관리자 ID"
          value={filters.adminId ?? ''}
          onChange={(event) => onChange({ adminId: parseAdminIdInput(event.target.value) })}
          className="w-28"
        />

        <Input
          type="date"
          aria-label="시작일"
          value={localFrom}
          onChange={(event) => handleFromChange(event.target.value)}
          className="w-36"
        />
        <span aria-hidden className="text-content-subtle">
          ~
        </span>
        <Input
          type="date"
          aria-label="종료일"
          value={localTo}
          onChange={(event) => handleToChange(event.target.value)}
          className="w-36"
        />

        {hasActiveFilter(filters) && (
          <Button variant="ghost" size="sm" onClick={handleReset}>
            초기화
          </Button>
        )}
      </div>

      {isInvalidRange && (
        <div className="mt-2">
          <FormError message="시작일은 종료일보다 늦을 수 없습니다." />
        </div>
      )}
    </div>
  );
}
