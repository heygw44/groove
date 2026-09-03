import { forwardRef, type SelectHTMLAttributes } from 'react';

interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  invalid?: boolean;
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { invalid = false, className = '', children, ...rest },
  ref,
) {
  const base =
    'h-10 w-full rounded-md border px-3 text-sm text-content ' +
    'focus:outline-none focus:ring-3 disabled:cursor-not-allowed disabled:opacity-60';
  const stateClass = invalid
    ? 'border-danger bg-danger-soft focus:border-danger focus:ring-danger/15'
    : 'border-line-strong bg-surface-sunken focus:border-content focus:bg-surface focus:ring-content/10';

  return (
    <select
      ref={ref}
      aria-invalid={invalid || undefined}
      className={`${base} ${stateClass} ${className}`}
      {...rest}
    >
      {children}
    </select>
  );
});
