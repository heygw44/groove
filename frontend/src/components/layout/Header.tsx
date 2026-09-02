import { Link } from 'react-router-dom';

export function Header() {
  return (
    <header className="flex items-center justify-between border-b border-neutral-200 px-4 py-3">
      <Link to="/" className="text-lg font-semibold">
        GROOVE
      </Link>
      <nav className="flex items-center gap-4 text-sm">
        <Link to="/products">상품</Link>
        <Link to="/limited-drops">한정반</Link>
        <Link to="/cart">장바구니</Link>
        <Link to="/mypage">마이페이지</Link>
      </nav>
    </header>
  );
}
