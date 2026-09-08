import axios from 'axios';
import { useParams } from 'react-router-dom';

import { ProductForm } from '@/components/admin/ProductForm';
import { EmptyState } from '@/components/common/EmptyState';
import { LinkButton } from '@/components/common/LinkButton';
import { QueryErrorState } from '@/components/common/QueryErrorState';
import { Spinner } from '@/components/common/Spinner';
import { useAdminProduct } from '@/hooks/queries/useAdminProduct';
import { getErrorCode } from '@/utils/apiError';

const ID_PATTERN = /^\d+$/;

export default function AdminProductEditPage() {
  const { id: idParam } = useParams();
  const isValidId = idParam !== undefined && ID_PATTERN.test(idParam);
  const id = isValidId ? Number(idParam) : -1;

  const { data: product, isPending, isError, error, refetch } = useAdminProduct(id);

  if (!isValidId) {
    return (
      <EmptyState
        title="존재하지 않는 상품입니다"
        action={
          <LinkButton to="/admin/products" variant="secondary">
            상품 목록으로
          </LinkButton>
        }
      />
    );
  }

  if (isPending) {
    return (
      <div className="flex min-h-48 items-center justify-center">
        <Spinner />
      </div>
    );
  }

  const isNotFoundStatus = axios.isAxiosError(error) && error.response?.status === 404;
  if (isError && (isNotFoundStatus || getErrorCode(error) === 'PRODUCT_NOT_FOUND')) {
    return (
      <EmptyState
        title="상품을 찾을 수 없습니다"
        action={
          <LinkButton to="/admin/products" variant="secondary">
            상품 목록으로
          </LinkButton>
        }
      />
    );
  }

  if (isError || !product) {
    return <QueryErrorState error={error} onRetry={refetch} title="상품을 불러오지 못했습니다" />;
  }

  return (
    <div>
      <h2 className="mb-5 text-[17px] font-bold tracking-tight">상품 수정</h2>
      <ProductForm product={product} />
    </div>
  );
}
