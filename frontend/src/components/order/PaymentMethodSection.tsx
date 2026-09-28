import type { PaymentMethodOption } from '@/types/payment';

const VIRTUAL_ACCOUNT_NOT_ALLOWED_REASON = '한정반은 무통장입금을 지원하지 않습니다.';

const PAYMENT_METHOD_OPTIONS: { value: PaymentMethodOption; label: string }[] = [
  { value: 'CARD', label: '신용·체크카드' },
  { value: 'TOSSPAY', label: '토스페이' },
  { value: 'NAVERPAY', label: '네이버페이' },
  { value: 'KAKAOPAY', label: '카카오페이' },
  { value: 'VIRTUAL_ACCOUNT', label: '무통장입금' },
];

interface PaymentMethodSectionProps {
  method: PaymentMethodOption;
  onChange: (method: PaymentMethodOption) => void;
  /** 한정반 초안은 가상계좌를 막는다. false 면 목록에서 숨기고 이유를 한 줄로 보여준다. */
  allowVirtualAccount: boolean;
}

/** 결제수단 라디오 목록. 주문서와 결제 이어하기 다이얼로그(#522)에서 함께 쓴다. */
export function PaymentMethodSection({
  method,
  onChange,
  allowVirtualAccount,
}: PaymentMethodSectionProps) {
  const options = allowVirtualAccount
    ? PAYMENT_METHOD_OPTIONS
    : PAYMENT_METHOD_OPTIONS.filter((option) => option.value !== 'VIRTUAL_ACCOUNT');

  return (
    <div>
      <h2 className="mb-3 text-base font-bold">결제수단</h2>
      <div className="flex flex-col gap-2">
        {options.map((option) => (
          <label
            key={option.value}
            className="flex cursor-pointer items-center gap-3 rounded-md border border-line px-4 py-3 has-[:checked]:border-content"
          >
            <input
              type="radio"
              name="payment-method"
              className="h-4 w-4 accent-content"
              checked={method === option.value}
              onChange={() => onChange(option.value)}
            />
            <span className="text-sm font-medium text-content">{option.label}</span>
          </label>
        ))}
      </div>
      {!allowVirtualAccount && (
        <p className="mt-2 text-xs text-content-muted">{VIRTUAL_ACCOUNT_NOT_ALLOWED_REASON}</p>
      )}
    </div>
  );
}
