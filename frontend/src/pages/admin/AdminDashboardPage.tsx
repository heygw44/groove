import { Link } from 'react-router-dom';

export default function AdminDashboardPage() {
  return (
    <div>
      <h2 className="text-[17px] font-bold tracking-tight">대시보드</h2>

      <div className="mt-4 grid gap-3 sm:grid-cols-2">
        <Link
          to="/admin/products"
          className="rounded-lg border border-line-strong p-5 no-underline hover:no-underline hover:bg-surface-muted"
        >
          <p className="font-bold text-content">상품 목록</p>
          <p className="mt-1 text-sm text-content-muted">등록된 상품을 조회하고 관리합니다.</p>
        </Link>
        <Link
          to="/admin/products/new"
          className="rounded-lg border border-line-strong p-5 no-underline hover:no-underline hover:bg-surface-muted"
        >
          <p className="font-bold text-content">상품 등록</p>
          <p className="mt-1 text-sm text-content-muted">새 상품을 등록합니다.</p>
        </Link>
      </div>
    </div>
  );
}
