import { Input } from '@/components/common/Input';
import { Select } from '@/components/common/Select';
import { BANK_OPTIONS } from '@/constants/banks';
import type { RefundAccount } from '@/types/order';

interface RefundAccountFieldsProps {
  value: RefundAccount;
  onChange: (value: RefundAccount) => void;
  error?: string;
  /** 한 화면에 여러 다이얼로그가 마운트돼도 label/id 가 겹치지 않게 하는 접두사. */
  idPrefix: string;
}

export function RefundAccountFields({
  value,
  onChange,
  error,
  idPrefix,
}: RefundAccountFieldsProps) {
  return (
    <div className="mb-4 flex flex-col gap-3">
      <p className="text-sm text-content-muted">
        무통장입금은 환불계좌로 직접 환불됩니다. 계좌 정보를 입력해주세요.
      </p>
      <div>
        <label htmlFor={`${idPrefix}-bank`} className="mb-1.5 block text-sm text-content-muted">
          은행
        </label>
        <Select
          id={`${idPrefix}-bank`}
          value={value.bankCode}
          onChange={(event) => onChange({ ...value, bankCode: event.target.value })}
        >
          <option value="">은행 선택</option>
          {BANK_OPTIONS.map((bank) => (
            <option key={bank.code} value={bank.code}>
              {bank.name}
            </option>
          ))}
        </Select>
      </div>
      <div>
        <label
          htmlFor={`${idPrefix}-account-number`}
          className="mb-1.5 block text-sm text-content-muted"
        >
          계좌번호
        </label>
        <Input
          id={`${idPrefix}-account-number`}
          inputMode="numeric"
          value={value.accountNumber}
          onChange={(event) =>
            onChange({ ...value, accountNumber: event.target.value.replace(/\D/g, '') })
          }
        />
      </div>
      <div>
        <label
          htmlFor={`${idPrefix}-holder-name`}
          className="mb-1.5 block text-sm text-content-muted"
        >
          예금주
        </label>
        <Input
          id={`${idPrefix}-holder-name`}
          value={value.holderName}
          onChange={(event) => onChange({ ...value, holderName: event.target.value })}
        />
      </div>
      {error && <p className="text-sm text-danger">{error}</p>}
    </div>
  );
}
