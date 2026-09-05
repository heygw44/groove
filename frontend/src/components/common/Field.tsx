import type { ReactNode } from 'react';

interface FieldProps {
  /** 자식 input 의 id 와 같아야 label · 에러 · 도움말이 연결된다. */
  htmlFor: string;
  label: string;
  required?: boolean;
  help?: string;
  error?: string;
  children: ReactNode;
}

export function Field({ htmlFor, label, required = false, help, error, children }: FieldProps) {
  const messageId = `${htmlFor}-message`;

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-sm font-medium text-content">
        {label}
        {required && (
          <span aria-hidden className="ml-0.5 text-accent">
            *
          </span>
        )}
      </label>
      {children}
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
