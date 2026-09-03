import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';

import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { WishlistCard } from '@/components/wishlist/WishlistCard';
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
  const { data, isPending, isError, isPlaceholderData, refetch } = useWishlist({
    page,
    size: PAGE_SIZE,
  });
  const toggleWishlistMutation = useToggleWishlist();
  const addCartItemMutation = useAddCartItem();

  const updatePage = (nextPage: number) => {
    setSearchParams({ page: String(nextPage) });
  };

  const handleRemove = (productId: number) => {
    // onSettled 가 위시리스트 목록을 다시 불러오므로 카드는 그때 사라진다.
    toggleWishlistMutation.mutate(
      { productId, wishlisted: true },
      {
        onSuccess: () => showToast('success', '위시리스트에서 뺐습니다.'),
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
      <h2 className="text-xl font-bold">위시리스트</h2>

      <p className="mt-4 text-sm text-content-muted">
        {isPending ? '불러오는 중…' : `총 ${data?.totalElements ?? 0}개`}
      </p>

      <div className="mt-3">
        {isPending && (
          <div className="flex min-h-48 items-center justify-center">
            <Spinner />
          </div>
        )}

        {!isPending && isError && (
          <EmptyState
            title="위시리스트를 불러오지 못했습니다."
            description="잠시 후 다시 시도해주세요."
            action={
              <Button variant="secondary" onClick={() => refetch()}>
                다시 시도
              </Button>
            }
          />
        )}

        {!isPending && !isError && data && data.content.length === 0 && (
          <EmptyState
            title="위시리스트가 비어 있습니다"
            action={
              <Link to="/products">
                <Button variant="secondary">상품 보러 가기</Button>
              </Link>
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
