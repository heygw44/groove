interface AdminPlaceholderProps {
  title: string;
}

export function AdminPlaceholder({ title }: AdminPlaceholderProps) {
  return <p className="text-sm text-content-muted">{title} 화면은 준비 중입니다.</p>;
}
