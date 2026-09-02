import { forwardRef, type InputHTMLAttributes } from 'react';

interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  invalid?: boolean;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { invalid = false, className = '', ...rest },
  ref,
) {
  const base =
    'h-10 w-full rounded-md border px-3 text-sm text-content placeholder:text-content-subtle ' +
    'focus:outline-none focus:ring-3 disabled:cursor-not-allowed disabled:opacity-60';
  const stateClass = invalid
    ? 'border-danger bg-danger-soft focus:border-danger focus:ring-danger/15'
    : 'border-line-strong bg-surface-sunken focus:border-content focus:bg-surface focus:ring-content/10';

  return (
    <input
      ref={ref}
      aria-invalid={invalid || undefined}
      className={`${base} ${stateClass} ${className}`}
      {...rest}
    />
  );
});
