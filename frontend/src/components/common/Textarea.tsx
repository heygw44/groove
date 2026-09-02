import { forwardRef, type TextareaHTMLAttributes } from 'react';

interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  invalid?: boolean;
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { invalid = false, className = '', ...rest },
  ref,
) {
  const base =
    'w-full rounded-md border px-3 py-2 text-sm text-content placeholder:text-content-subtle ' +
    'focus:outline-none focus:ring-3 disabled:cursor-not-allowed disabled:opacity-60';
  const stateClass = invalid
    ? 'border-danger bg-danger-soft focus:border-danger focus:ring-danger/15'
    : 'border-line-strong bg-surface-sunken focus:border-content focus:bg-surface focus:ring-content/10';

  return (
    <textarea
      ref={ref}
      aria-invalid={invalid || undefined}
      className={`${base} ${stateClass} ${className}`}
      {...rest}
    />
  );
});
