interface FormErrorProps {
  message?: string;
}

export function FormError({ message }: FormErrorProps) {
  if (!message) {
    return null;
  }

  return (
    <p
      role="alert"
      className="rounded-md border border-danger-line bg-danger-soft px-3 py-2.5 text-sm text-danger"
    >
      {message}
    </p>
  );
}
