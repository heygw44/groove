import { Children, cloneElement, isValidElement, type ReactElement, type ReactNode } from 'react';

interface FieldProps {
  /** 자식 input 의 id 와 같아야 label · 에러 · 도움말이 연결된다. */
  htmlFor: string;
  label: string;
  required?: boolean;
  help?: string;
  error?: string;
  children: ReactNode;
  /** children 이 단일 엘리먼트가 아니라 aria-describedby 를 주입할 수 없을 때(예: input + 글자수 표시) 쓰는 보조 슬롯. */
  trailing?: ReactNode;
}

interface DescribableElementProps {
  'aria-describedby'?: string;
  'aria-required'?: boolean;
}

export function Field({
  htmlFor,
  label,
  required = false,
  help,
  error,
  children,
  trailing,
}: FieldProps) {
  const messageId = `${htmlFor}-message`;
  const hasMessage = Boolean(error || help);

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-sm font-medium text-content">
        {label}
        {required && (
          <>
            <span aria-hidden className="ml-0.5 text-accent">
              *
            </span>
            <span className="sr-only">(필수)</span>
          </>
        )}
      </label>
      {withFieldA11y(children, hasMessage ? messageId : undefined, required)}
      {trailing}
      {error ? (
        <p id={messageId} role="alert" className="text-xs text-danger">
          {error}
        </p>
      ) : (
        help && (
          <p id={messageId} className="text-xs text-content-muted">
            {help}
          </p>
        )
      )}
    </div>
  );
}

/** children 이 단일 엘리먼트일 때만 aria-describedby/aria-required 를 주입한다(복수 자식이면 대상이 모호해 건너뛴다). */
function withFieldA11y(children: ReactNode, messageId: string | undefined, required: boolean) {
  if (Children.count(children) !== 1 || !isValidElement(children)) {
    return children;
  }

  const element = children as ReactElement<DescribableElementProps>;
  const describedBy = [element.props['aria-describedby'], messageId].filter(Boolean).join(' ');

  return cloneElement(element, {
    ...(describedBy ? { 'aria-describedby': describedBy } : {}),
    ...(required ? { 'aria-required': true } : {}),
  });
}
