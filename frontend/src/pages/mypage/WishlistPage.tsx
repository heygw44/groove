import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { EmptyState } from '@/components/common/EmptyState';
import { LinkButton } from '@/components/common/LinkButton';
import { Pagination } from '@/components/common/Pagination';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { useToast } from '@/components/common/toastContext';
import { WishlistCard, WishlistCardSkeleton } from '@/components/wishlist/WishlistCard';
import { useAddCartItem } from '@/hooks/mutations/useCartMutations';
import { useToggleWishlist } from '@/hooks/mutations/useWishlistMutations';
import { useWishlist } from '@/hooks/queries/useWishlist';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';

const PAGE_SIZE = 12;

const parsePage = (searchParams: URLSearchParams) => {
  const raw = Number(searchParams.get('page'));
  return Number.isInteger(raw) && raw >= 0 ? raw : 0;
};

export default function WishlistPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = parsePage(searchParams);
  const [addingProductId, setAddingProductId] = useState<number | undefined>(undefined);

  const { showToast } = useToast();
  const { data, isPending, isError, error, isPlaceholderData, refetch } = useWishlist({
    page,
    size: PAGE_SIZE,
  });
  const toggleWishlistMutation = useToggleWishlist();
  const addCartItemMutation = useAddCartItem();

  const updatePage = (nextPage: number) => {
    setSearchParams({ page: String(nextPage) });
  };

  const handleRemove = (productId: number) => {
    // onSettled 가 찜 목록을 다시 불러오므로 카드는 그때 사라진다.
    toggleWishlistMutation.mutate(
      { productId, wishlisted: true },
      {
        onSuccess: () => showToast('success', '찜을 해제했습니다.'),
        onError: (error) => {
          const code = getErrorCode(error);
          if (code !== 'WISHLIST_NOT_FOUND') {
            showToast('error', getErrorMessage(error));
          }
        },
      },
    );
  };

  const handleAddToCart = (productId: number) => {
    setAddingProductId(productId);
    addCartItemMutation.mutate(
      { productId, quantity: 1 },
      {
        onSuccess: () => showToast('success', '장바구니에 담았습니다.'),
        onError: (error) => showToast('error', getErrorMessage(error)),
        onSettled: () => setAddingProductId(undefined),
      },
    );
  };

  return (
    <div>
      <h2 className="text-xl font-bold">찜 목록</h2>

      <p className="mt-4 text-sm text-content-muted">
        {isPending ? '불러오는 중…' : `총 ${data?.totalElements ?? 0}개`}
      </p>

      <div className="mt-3">
        {isPending && (
          <div className="grid grid-cols-2 gap-5 sm:grid-cols-3 lg:grid-cols-4">
            {Array.from({ length: PAGE_SIZE }, (_, index) => (
              <WishlistCardSkeleton key={index} />
            ))}
          </div>
        )}

        {!isPending && isError && (
          <QueryErrorState error={error} onRetry={refetch} title="찜 목록을 불러오지 못했습니다." />
        )}

        {!isPending && !isError && data && data.content.length === 0 && (
          <EmptyState
            title="찜한 상품이 없습니다"
            action={
              <LinkButton to="/products" variant="secondary">
                상품 보러 가기
              </LinkButton>
            }
          />
        )}

        {!isPending && !isError && data && data.content.length > 0 && (
          <div className={isPlaceholderData ? 'opacity-60' : ''}>
            <div className="grid grid-cols-2 gap-5 sm:grid-cols-3 lg:grid-cols-4">
              {data.content.map((item) => (
                <WishlistCard
                  key={item.id}
                  item={item}
                  onRemove={() => handleRemove(item.productId)}
                  onAddToCart={() => handleAddToCart(item.productId)}
                  addingToCart={addingProductId === item.productId}
                />
              ))}
            </div>

            <div className="mt-6">
              <Pagination page={page} totalPages={data.totalPages} onChange={updatePage} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
