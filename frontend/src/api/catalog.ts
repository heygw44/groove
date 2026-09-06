import { client, unwrap } from '@/api/client';
import type { ApiResponse, PageResponse } from '@/types/api';
import type {
  CatalogImportJob,
  CatalogImportJobListParams,
  CatalogImportJobRequest,
  CatalogImportJobStartResponse,
  CatalogImportRequest,
  CatalogImportResponse,
  CatalogLookupItem,
  CatalogLookupParams,
  CatalogReleaseDetail,
} from '@/types/catalog';

export const getCatalogLookup = (params: CatalogLookupParams) =>
  unwrap(
    client.get<ApiResponse<PageResponse<CatalogLookupItem>>>('/admin/catalog/lookup', { params }),
  );

export const getCatalogRelease = (discogsReleaseId: number) =>
  unwrap(
    client.get<ApiResponse<CatalogReleaseDetail>>(
      `/admin/catalog/releases/${discogsReleaseId}`,
    ),
  );

export const importCatalogRelease = (payload: CatalogImportRequest) =>
  unwrap(client.post<ApiResponse<CatalogImportResponse>>('/admin/catalog/imports', payload));

export const startCatalogImportJob = (payload: CatalogImportJobRequest) =>
  unwrap(
    client.post<ApiResponse<CatalogImportJobStartResponse>>('/admin/catalog/import-jobs', payload),
  );

export const getCatalogImportJobs = (params: CatalogImportJobListParams) =>
  unwrap(
    client.get<ApiResponse<PageResponse<CatalogImportJob>>>('/admin/catalog/import-jobs', {
      params,
    }),
  );

export const getCatalogImportJob = (jobExecutionId: number) =>
  unwrap(
    client.get<ApiResponse<CatalogImportJob>>(`/admin/catalog/import-jobs/${jobExecutionId}`),
  );

export const restartCatalogImportJob = (jobExecutionId: number) =>
  unwrap(
    client.post<ApiResponse<CatalogImportJobStartResponse>>(
      `/admin/catalog/import-jobs/${jobExecutionId}/restart`,
    ),
  );
