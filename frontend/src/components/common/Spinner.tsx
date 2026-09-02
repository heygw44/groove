export function Spinner() {
  return (
    <div
      role="status"
      aria-label="로딩 중"
      className="h-6 w-6 animate-spin rounded-full border-2 border-neutral-300 border-t-neutral-900"
    />
  );
}
