import { useId, useState } from 'react';

import { Input } from '@/components/common/Input';
import { Spinner } from '@/components/common/Spinner';
import { useProducts } from '@/hooks/queries/useProducts';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import type { ProductSummary } from '@/types/product';

interface ProductSearchSelectProps {
  value?: number;
  /** `GET /products/{id}` 조회로 얻은 제목. 방금 고른 상품(로컬 state)이 이보다 우선한다. */
  selectedTitle?: string;
  onChange: (product: ProductSummary | undefined) => void;
  /** 이미 한정반 드롭이 등록된 상품 id. 목록에서 "등록됨"으로 표시하고 선택할 수 없게 한다. */
  disabledProductIds?: Set<number>;
  id?: string;
  invalid?: boolean;
  disabled?: boolean;
}

export function ProductSearchSelect({
  value,
  selectedTitle,
  onChange,
  disabledProductIds,
  id,
  invalid = false,
  disabled = false,
}: ProductSearchSelectProps) {
  const [pickedProduct, setPickedProduct] = useState<ProductSummary | undefined>(undefined);
  const [keyword, setKeyword] = useState('');
  const [open, setOpen] = useState(false);
  const debouncedKeyword = useDebouncedValue(keyword, 300);
  const inputId = useId();

  const { data, isFetching } = useProducts({ keyword: debouncedKeyword || undefined, size: 10 });
  const products = data?.content ?? [];

  const handleSelect = (product: ProductSummary) => {
    if (disabledProductIds?.has(product.id)) {
      return;
    }
    setPickedProduct(product);
    setKeyword('');
    setOpen(false);
    onChange(product);
  };

  const handleClear = () => {
    setPickedProduct(undefined);
    onChange(undefined);
  };

  if (value !== undefined) {
    const displayName = pickedProduct?.title ?? selectedTitle ?? `상품 #${value}`;
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-accent-soft py-1 pl-3 pr-1.5 text-sm text-accent-hover">
        {displayName}
        <button
          type="button"
          onClick={handleClear}
          disabled={disabled}
          aria-label="상품 선택 해제"
          className="rounded-full p-0.5 hover:bg-accent-hover/10 disabled:cursor-not-allowed"
        >
          ×
        </button>
      </span>
    );
  }

  return (
    <div className="relative">
      <Input
        id={id ?? inputId}
        role="combobox"
        aria-expanded={open}
        aria-autocomplete="list"
        invalid={invalid}
        disabled={disabled}
        placeholder="상품 검색"
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
        onFocus={() => setOpen(true)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
      />
      {open && (
        <ul className="absolute z-10 mt-1 max-h-56 w-full overflow-y-auto rounded-md border border-line bg-surface py-1 shadow-lg">
          {isFetching && (
            <li className="flex items-center justify-center px-3 py-3">
              <Spinner size="sm" />
            </li>
          )}
          {!isFetching && products.length === 0 && (
            <li className="px-3 py-2.5 text-sm text-content-subtle">검색 결과가 없습니다.</li>
          )}
          {!isFetching &&
            products.map((product) => {
              const isRegistered = disabledProductIds?.has(product.id) ?? false;
              return (
                <li key={product.id}>
                  <button
                    type="button"
                    disabled={isRegistered}
                    onMouseDown={(event) => event.preventDefault()}
                    onClick={() => handleSelect(product)}
                    className="flex w-full items-center justify-between px-3 py-2 text-left text-sm hover:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:bg-transparent"
                  >
                    <span>
                      {product.title}
                      <span className="ml-1.5 text-content-muted">{product.artistName}</span>
                    </span>
                    {isRegistered && <span className="text-xs text-content-subtle">등록됨</span>}
                  </button>
                </li>
              );
            })}
        </ul>
      )}
    </div>
  );
}
