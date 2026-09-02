import { Link } from 'react-router-dom';

export function Header() {
  return (
    <header className="border-b border-line bg-surface">
      <div className="mx-auto flex max-w-6xl items-center justify-between gap-6 px-4 py-3">
        <Link to="/" className="text-lg font-bold tracking-[0.14em]">
          GROOVE
        </Link>
        <nav className="flex items-center gap-5 text-sm text-content-muted">
          <Link to="/products" className="hover:text-content">
            상품
          </Link>
          <Link to="/limited-drops" className="hover:text-content">
            한정반
          </Link>
          <Link to="/cart" className="hover:text-content">
            장바구니
          </Link>
          <Link to="/mypage" className="hover:text-content">
            마이페이지
          </Link>
        </nav>
      </div>
    </header>
  );
}
