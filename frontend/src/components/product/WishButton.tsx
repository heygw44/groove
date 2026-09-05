import type { MouseEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

import { useToast } from '@/components/common/toastContext';
import { useToggleWishlist } from '@/hooks/mutations/useWishlistMutations';
import { useAuthStore } from '@/store/authStore';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';

type WishButtonSize = 'sm' | 'md';

interface WishToggleProps {
  pressed: boolean;
  onClick: (event: MouseEvent<HTMLButtonElement>) => void;
  size?: WishButtonSize;
  className?: string;
  disabled?: boolean;
}

const SIZE_CLASS: Record<WishButtonSize, string> = {
  sm: 'h-8 w-8 rounded-full bg-surface/90',
  md: 'h-10 w-10 rounded-md border border-line-strong bg-surface',
};

export function WishToggle({
  pressed,
  onClick,
  size = 'md',
  className = '',
  disabled = false,
}: WishToggleProps) {
  return (
    <button
      type="button"
      aria-pressed={pressed}
      aria-label={pressed ? '위시리스트에서 빼기' : '위시리스트에 담기'}
      disabled={disabled}
      onClick={onClick}
      className={`inline-flex items-center justify-center transition-colors disabled:cursor-not-allowed disabled:opacity-45 ${SIZE_CLASS[size]} ${
        pressed ? 'text-danger' : 'text-content-subtle hover:text-content'
      } ${className}`}
    >
      <svg
        width="18"
        height="18"
        viewBox="0 0 24 24"
        fill={pressed ? 'currentColor' : 'none'}
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden
      >
        <path d="M12 20.5s-7.5-4.6-10-9.3C.4 8 1.9 4.5 5.3 3.7c2-.5 4 .3 5.2 2 .3.4.6.9.8 1.4.2-.5.5-1 .8-1.4 1.2-1.7 3.2-2.5 5.2-2 3.4.8 4.9 4.3 3.3 7.5-2.5 4.7-10 9.3-10 9.3Z" />
      </svg>
    </button>
  );
}

interface WishButtonProps {
  productId: number;
  wishlisted?: boolean;
  size?: WishButtonSize;
  className?: string;
}

export function WishButton({
  productId,
  wishlisted,
  size = 'md',
  className = '',
}: WishButtonProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { showToast } = useToast();
  const isLoggedIn = useAuthStore((s) => Boolean(s.accessToken));
  const toggle = useToggleWishlist();

  const pressed = Boolean(wishlisted);

  const handleClick = (event: MouseEvent<HTMLButtonElement>) => {
    // ProductCard 안에 있을 때는 카드 전체가 Link 라 클릭이 새어나가면 이동해버린다.
    event.preventDefault();
    event.stopPropagation();

    if (!isLoggedIn) {
      const redirect = encodeURIComponent(`${location.pathname}${location.search}`);
      navigate(`/login?redirect=${redirect}`);
      return;
    }

    toggle.mutate(
      { productId, wishlisted: pressed },
      {
        onError: (error) => {
          const code = getErrorCode(error);
          if (code !== 'WISHLIST_ALREADY_EXISTS' && code !== 'WISHLIST_NOT_FOUND') {
            showToast('error', getErrorMessage(error));
          }
        },
      },
    );
  };

  return <WishToggle pressed={pressed} onClick={handleClick} size={size} className={className} />;
}
