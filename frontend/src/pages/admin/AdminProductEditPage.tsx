import axios from 'axios';
import { Link, useParams } from 'react-router-dom';

import { ProductForm } from '@/components/admin/ProductForm';
import { Button } from '@/components/common/Button';
import { EmptyState } from '@/components/common/EmptyState';
import { Spinner } from '@/components/common/Spinner';
import { useProduct } from '@/hooks/queries/useProduct';
import { getErrorCode, getErrorMessage } from '@/utils/apiError';

const NOT_FOUND_CODES = new Set(['PRODUCT_NOT_FOUND', 'PRODUCT_HIDDEN']);

const ID_PATTERN = /^\d+$/;

export default function AdminProductEditPage() {
  const { id: idParam } = useParams();
  const isValidId = idParam !== undefined && ID_PATTERN.test(idParam);
  const id = isValidId ? Number(idParam) : -1;

  const { data: product, isPending, isError, error, refetch } = useProduct(id);

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
  if (isError && (isNotFoundStatus || NOT_FOUND_CODES.has(getErrorCode(error) ?? ''))) {
    return (
      <EmptyState
        title="숨김 처리된 상품은 수정할 수 없습니다."
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
