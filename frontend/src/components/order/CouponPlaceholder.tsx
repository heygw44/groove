import { Field } from '@/components/common/Field';
import { Select } from '@/components/common/Select';

export function CouponPlaceholder() {
  return (
    <Field htmlFor="coupon" label="쿠폰" help="쿠폰은 곧 지원됩니다.">
      <Select id="coupon" disabled defaultValue="">
        <option value="">쿠폰은 곧 지원됩니다</option>
      </Select>
    </Field>
  );
}
