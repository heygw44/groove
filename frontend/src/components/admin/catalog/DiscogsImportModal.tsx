import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';

import { getCatalogLookup, getCatalogRelease } from '@/api/catalog';
import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { Input } from '@/components/common/Input';
import { Modal } from '@/components/common/Modal';
import { Spinner } from '@/components/common/Spinner';
import { adminCatalogLookupKeys } from '@/hooks/queries/queryKeys';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import type { CatalogLookupParams, CatalogReleaseDetail } from '@/types/catalog';
import { getErrorMessage } from '@/utils/apiError';

interface DiscogsImportModalProps {
  onClose: () => void;
  /** 후보를 골라 상세 조회까지 끝난 뒤 호출된다. 폼 프리필은 호출부(ProductForm)가 맡는다. */
  onImport: (detail: CatalogReleaseDetail) => void;
}

interface SearchInputs {
  barcode: string;
  catalogNo: string;
  keyword: string;
}

const EMPTY_SEARCH: SearchInputs = { barcode: '', catalogNo: '', keyword: '' };

/**
 * 호출부(ProductForm)가 열려 있을 때만 이 컴포넌트를 마운트한다 - 검색어·페이지 state 는
 * 마운트 시점 초기값으로 자연스럽게 리셋되므로, "열릴 때 리셋" 을 위한 effect 가 필요 없다.
 */
export function DiscogsImportModal({ onClose, onImport }: DiscogsImportModalProps) {
  const [search, setSearch] = useState<SearchInputs>(EMPTY_SEARCH);
  const [page, setPage] = useState(0);
  const debouncedSearch = useDebouncedValue(search, 300);

  // 검색어가 바뀌면 그 자리에서 페이지도 0으로 되돌린다(이전 페이지가 새 검색 결과에 대해 남지 않도록).
  const updateSearch = (patch: Partial<SearchInputs>) => {
    setSearch((prev) => ({ ...prev, ...patch }));
    setPage(0);
  };

  const trimmed: SearchInputs = {
    barcode: debouncedSearch.barcode.trim(),
    catalogNo: debouncedSearch.catalogNo.trim(),
    keyword: debouncedSearch.keyword.trim(),
  };
  const hasQuery = Boolean(trimmed.barcode || trimmed.catalogNo || trimmed.keyword);

  const lookupParams: CatalogLookupParams = {
    barcode: trimmed.barcode || undefined,
    catalogNo: trimmed.catalogNo || undefined,
    query: trimmed.keyword || undefined,
    page,
  };

  const { data, isFetching, isError, error } = useQuery({
    queryKey: adminCatalogLookupKeys.list(lookupParams),
    queryFn: () => getCatalogLookup(lookupParams),
    enabled: hasQuery,
  });

  const releaseMutation = useMutation({
    mutationFn: (discogsReleaseId: number) => getCatalogRelease(discogsReleaseId),
    onSuccess: (detail) => {
      onImport(detail);
    },
  });

  const items = data?.content ?? [];
  const hasNextPage = data ? page + 1 < data.totalPages : false;

  const handleClose = () => {
    releaseMutation.reset();
    onClose();
  };

  return (
    <Modal
      open
      onClose={handleClose}
      title="Discogs에서 불러오기"
      description="바코드, 카탈로그 번호, 키워드 중 하나 이상 입력해주세요."
      size="lg"
      footer={
        <Button variant="secondary" onClick={handleClose}>
          닫기
        </Button>
      }
    >
      <div className="flex flex-col gap-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
          <Field htmlFor="discogs-barcode" label="바코드">
            <Input
              id="discogs-barcode"
              value={search.barcode}
              onChange={(event) => updateSearch({ barcode: event.target.value })}
            />
          </Field>
          <Field htmlFor="discogs-catalog-no" label="카탈로그 번호">
            <Input
              id="discogs-catalog-no"
              value={search.catalogNo}
              onChange={(event) => updateSearch({ catalogNo: event.target.value })}
            />
          </Field>
          <Field htmlFor="discogs-keyword" label="키워드">
            <Input
              id="discogs-keyword"
              value={search.keyword}
              onChange={(event) => updateSearch({ keyword: event.target.value })}
            />
          </Field>
        </div>

        {releaseMutation.isError && (
          <p className="text-sm text-danger">{getErrorMessage(releaseMutation.error)}</p>
        )}

        {!hasQuery && <p className="text-sm text-content-subtle">검색 조건을 입력해주세요.</p>}

        {hasQuery && isFetching && (
          <div className="flex items-center justify-center py-8">
            <Spinner />
          </div>
        )}

        {hasQuery && !isFetching && isError && (
          <p className="text-sm text-danger">{getErrorMessage(error)}</p>
        )}

        {hasQuery && !isFetching && !isError && items.length === 0 && (
          <p className="text-sm text-content-subtle">검색 결과가 없습니다.</p>
        )}

        {hasQuery && !isFetching && !isError && items.length > 0 && (
          <>
            <ul className="flex max-h-96 flex-col gap-2 overflow-y-auto">
              {items.map((item) => {
                const isBusy =
                  releaseMutation.isPending && releaseMutation.variables === item.discogsReleaseId;
                return (
                  <li key={item.discogsReleaseId}>
                    <button
                      type="button"
                      disabled={item.alreadyImported || releaseMutation.isPending}
                      onClick={() => releaseMutation.mutate(item.discogsReleaseId)}
                      className="flex w-full items-center gap-3 rounded-md border border-line px-3 py-2 text-left text-sm hover:bg-surface-muted disabled:cursor-not-allowed disabled:opacity-50 disabled:hover:bg-transparent"
                    >
                      {item.thumbUrl ? (
                        <img
                          src={item.thumbUrl}
                          alt=""
                          className="h-12 w-12 shrink-0 rounded object-cover"
                        />
                      ) : (
                        <div className="h-12 w-12 shrink-0 rounded bg-surface-muted" />
                      )}
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-medium">
                          {item.title ?? '제목 없음'}
                        </span>
                        <span className="block truncate text-content-muted">
                          {[item.artist, item.year, item.catalogNo, item.label]
                            .filter(Boolean)
                            .join(' · ')}
                        </span>
                      </span>
                      {item.alreadyImported ? (
                        <span className="shrink-0 text-xs text-content-subtle">등록됨</span>
                      ) : (
                        isBusy && <Spinner size="sm" />
                      )}
                    </button>
                  </li>
                );
              })}
            </ul>

            {(page > 0 || hasNextPage) && (
              <div className="flex justify-center gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage((prev) => Math.max(0, prev - 1))}
                >
                  이전
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={!hasNextPage}
                  onClick={() => setPage((prev) => prev + 1)}
                >
                  다음
                </Button>
              </div>
            )}
          </>
        )}
      </div>
    </Modal>
  );
}
