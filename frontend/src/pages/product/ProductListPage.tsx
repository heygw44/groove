import { useState } from 'react';

import { Button } from '@/components/common/Button';
import { Drawer } from '@/components/common/Drawer';
import { EmptyState } from '@/components/common/EmptyState';
import { Pagination } from '@/components/common/Pagination';
import { ActiveFilterChips } from '@/components/product/ActiveFilterChips';
import { ProductCard, ProductCardSkeleton } from '@/components/product/ProductCard';
import { ProductFilterPanel } from '@/components/product/ProductFilterPanel';
import { ProductSortSelect } from '@/components/product/ProductSortSelect';
import { useProducts } from '@/hooks/queries/useProducts';
import { useArtist } from '@/hooks/queries/useReferences';
import { useProductFilters } from '@/hooks/useProductFilters';
import { countActiveFilters, toProductListParams } from '@/utils/productFilters';

const SKELETON_COUNT = 8;

export default function ProductListPage() {
  const { filters, update, setPage, reset } = useProductFilters();
  const [isFilterDrawerOpen, setIsFilterDrawerOpen] = useState(false);

  const { data, isPending, isError, isPlaceholderData, refetch } = useProducts(
    toProductListParams(filters),
  );
  const { data: selectedArtist } = useArtist(filters.artistId);

  const handlePageChange = (page: number) => {
    setPage(page);
    window.scrollTo({ top: 0 });
  };

  const activeFilterCount = countActiveFilters(filters);

  const filterPanel = (
    <ProductFilterPanel
      filters={filters}
      onUpdate={update}
      onReset={reset}
      artistSelectedName={selectedArtist?.name}
    />
  );

  return (
    <div className="mx-auto max-w-6xl px-4 py-8">
      <h1 className="mb-6 text-2xl font-bold tracking-tight">상품 목록</h1>

      <div className="grid gap-8 lg:grid-cols-[220px_minmax(0,1fr)]">
        <aside className="hidden lg:block">
          <div className="sticky top-6 rounded-lg border border-line bg-surface p-4">
            {filterPanel}
          </div>
        </aside>

        <div>
          <div className="mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-line pb-3">
            <p className="text-sm text-content-muted">
              {isPending ? '불러오는 중…' : `총 ${data?.totalElements ?? 0}개`}
            </p>
            <div className="flex items-center gap-2">
              <Button
                variant="secondary"
                size="sm"
                className="lg:hidden"
                onClick={() => setIsFilterDrawerOpen(true)}
              >
                필터{activeFilterCount > 0 ? ` (${activeFilterCount})` : ''}
              </Button>
              <ProductSortSelect value={filters.sort} onChange={(sort) => update({ sort })} />
            </div>
          </div>

          <ActiveFilterChips
            filters={filters}
            artistSelectedName={selectedArtist?.name}
            onUpdate={update}
            onClearAll={reset}
          />

          {isPending && (
            <div className="grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4">
              {Array.from({ length: SKELETON_COUNT }).map((_, index) => (
                <ProductCardSkeleton key={index} />
              ))}
            </div>
          )}

          {!isPending && isError && (
            <EmptyState
              title="상품을 불러오지 못했습니다."
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
              title="조건에 맞는 상품이 없습니다."
              description="필터를 조정해보세요."
              action={
                <Button variant="secondary" onClick={reset}>
                  필터 초기화
                </Button>
              }
            />
          )}

          {!isPending && !isError && data && data.content.length > 0 && (
            <>
              <div
                className={`grid grid-cols-2 gap-4 md:grid-cols-3 lg:grid-cols-4 ${
                  isPlaceholderData ? 'opacity-60' : ''
                }`}
              >
                {data.content.map((product) => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </div>

              <div className="mt-8">
                <Pagination
                  page={filters.page}
                  totalPages={data.totalPages}
                  onChange={handlePageChange}
                />
              </div>
            </>
          )}
        </div>
      </div>

      <Drawer open={isFilterDrawerOpen} onClose={() => setIsFilterDrawerOpen(false)} title="필터">
        {filterPanel}
      </Drawer>
    </div>
  );
}
