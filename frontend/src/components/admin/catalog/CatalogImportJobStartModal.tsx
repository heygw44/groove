import { zodResolver } from '@hookform/resolvers/zod';
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';

import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { Modal } from '@/components/common/Modal';
import { useStartCatalogImportJob } from '@/hooks/mutations/useAdminCatalogMutations';
import {
  catalogImportJobStartFormSchema,
  EMPTY_CATALOG_IMPORT_JOB_START_FORM_VALUES,
  toCatalogImportJobStartPayload,
  type CatalogImportJobStartFormValues,
} from '@/schemas/catalog';
import { applyFieldErrors, getErrorMessage } from '@/utils/apiError';

interface CatalogImportJobStartModalProps {
  open: boolean;
  onClose: () => void;
  onStarted: () => void;
  onError: (message: string) => void;
}

export function CatalogImportJobStartModal({
  open,
  onClose,
  onStarted,
  onError,
}: CatalogImportJobStartModalProps) {
  const startMutation = useStartCatalogImportJob();

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<CatalogImportJobStartFormValues>({
    resolver: zodResolver(catalogImportJobStartFormSchema),
    mode: 'onBlur',
    defaultValues: EMPTY_CATALOG_IMPORT_JOB_START_FORM_VALUES,
  });

  useEffect(() => {
    if (open) {
      reset(EMPTY_CATALOG_IMPORT_JOB_START_FORM_VALUES);
    }
  }, [open, reset]);

  const isBusy = isSubmitting || startMutation.isPending;

  const onSubmit = handleSubmit((values) => {
    startMutation.mutate(toCatalogImportJobStartPayload(values), {
      onSuccess: () => {
        onStarted();
        onClose();
      },
      onError: (error) => {
        if (!applyFieldErrors(error, setError)) {
          onError(getErrorMessage(error));
        }
      },
    });
  });

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="수집 실행"
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={isBusy}>
            취소
          </Button>
          <Button type="submit" form="catalog-import-job-start-form" loading={isBusy}>
            실행
          </Button>
        </>
      }
    >
      <form
        id="catalog-import-job-start-form"
        className="flex flex-col gap-4"
        onSubmit={onSubmit}
        noValidate
      >
        <FormError message={errors.root?.serverError?.message} />

        <Field
          htmlFor="catalog-import-job-master-id"
          label="Discogs 마스터 ID"
          required
          error={errors.discogsMasterId?.message}
        >
          <Input
            id="catalog-import-job-master-id"
            inputMode="numeric"
            invalid={Boolean(errors.discogsMasterId)}
            {...register('discogsMasterId')}
          />
        </Field>

        <Field
          htmlFor="catalog-import-job-default-price"
          label="기본가"
          required
          help="원 단위"
          error={errors.defaultPrice?.message}
        >
          <Input
            id="catalog-import-job-default-price"
            inputMode="numeric"
            invalid={Boolean(errors.defaultPrice)}
            {...register('defaultPrice')}
          />
        </Field>
      </form>
    </Modal>
  );
}
