import axios from 'axios';
import { Link, useParams } from 'react-router-dom';

import { ProductForm } from '@/components/admin/ProductForm';
import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Spinner } from '@/components/common/Spinner';
import { useAdminProduct } from '@/hooks/queries/useAdminProduct';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';

const ID_PATTERN = /^\d+$/;

export default function AdminProductEditPage() {
  const { id: idParam } = useParams();
  const isValidId = idParam !== undefined && ID_PATTERN.test(idParam);
  const id = isValidId ? Number(idParam) : -1;

  const { data: product, isPending, isError, error, refetch } = useAdminProduct(id);

  if (!isValidId) {
    return <p className="text-sm text-danger">존재하지 않는 상품입니다.</p>;
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
        title="상품을 찾을 수 없습니다."
        action={
          <Link to="/admin/products" className="no-underline hover:no-underline">
            <Button variant="secondary">상품 목록으로</Button>
          </Link>
        }
      />
    );
  }

  if (isError || !product) {
    return (
      <EmptyState
        title="상품을 불러오지 못했습니다."
        description={getErrorMessage(error)}
        action={
          <Button variant="secondary" onClick={() => refetch()}>
            다시 시도
          </Button>
        }
      />
    );
  }

  return (
    <div>
      <h2 className="mb-5 text-[17px] font-bold tracking-tight">상품 수정</h2>
      <ProductForm product={product} />
    </div>
  );
}
