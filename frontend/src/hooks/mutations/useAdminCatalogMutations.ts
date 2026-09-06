import { useMutation, useQueryClient } from '@tanstack/react-query';

import { restartCatalogImportJob, startCatalogImportJob } from '@/api/catalog';
import { adminCatalogImportJobKeys } from '@/hooks/queries/queryKeys';
import type { CatalogImportJobRequest } from '@/types/catalog';

const useInvalidateCatalogImportJobs = () => {
  const queryClient = useQueryClient();
  return () => {
    queryClient.invalidateQueries({ queryKey: adminCatalogImportJobKeys.lists });
  };
};

export const useStartCatalogImportJob = () => {
  const invalidate = useInvalidateCatalogImportJobs();

  return useMutation({
    mutationFn: (payload: CatalogImportJobRequest) => startCatalogImportJob(payload),
    onSuccess: () => invalidate(),
  });
};

export const useRestartCatalogImportJob = () => {
  const invalidate = useInvalidateCatalogImportJobs();

  return useMutation({
    mutationFn: (jobExecutionId: number) => restartCatalogImportJob(jobExecutionId),
    onSuccess: () => invalidate(),
  });
};
