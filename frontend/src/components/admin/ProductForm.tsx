import { zodResolver } from '@hookform/resolvers/zod';
import { Controller, useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';

import { GenreCheckboxGroup } from '@/components/admin/GenreCheckboxGroup';
import { ProductImageUploader } from '@/components/admin/ProductImageUploader';
import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { Select } from '@/components/common/Select';
import { Textarea } from '@/components/common/Textarea';
import { useToast } from '@/components/common/Toast';
import { ArtistSearchSelect } from '@/components/product/ArtistSearchSelect';
import { useCreateProduct, useUpdateProduct } from '@/hooks/mutations/useAdminProductMutations';
import { useGenres, useLabels } from '@/hooks/queries/useReferences';
import {
  EMPTY_PRODUCT_FORM_VALUES,
  productCreateSchema,
  productFormSchema,
  toCreatePayload,
  toFormValues,
  toUpdatePayload,
  type ProductFormValues,
} from '@/schemas/product';
import type { ProductDetail } from '@/types/product';
import { applyFieldErrors, getErrorMessage } from '@/utils/apiError';

interface ProductFormProps {
  /** 있으면 수정, 없으면 등록. 페이지가 등록/수정으로 분리되어 있어 이 값은 마운트 중 바뀌지 않는다. */
  product?: ProductDetail;
}

export function ProductForm({ product }: ProductFormProps) {
  const isEdit = Boolean(product);
  const navigate = useNavigate();
  const { showToast } = useToast();
  const { data: genres } = useGenres();
  const { data: labels } = useLabels();
  const createMutation = useCreateProduct();
  const updateMutation = useUpdateProduct();

  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ProductFormValues>({
    resolver: zodResolver(product ? productFormSchema : productCreateSchema),
    mode: 'onBlur',
    defaultValues: product ? toFormValues(product) : EMPTY_PRODUCT_FORM_VALUES,
  });

  const isMutating = createMutation.isPending || updateMutation.isPending;

  const handleError = (error: unknown) => {
    if (!applyFieldErrors(error, setError)) {
      setError('root.serverError', { message: getErrorMessage(error) });
    }
  };

  const onSubmit = handleSubmit((values) => {
    if (isEdit && product) {
      updateMutation.mutate(
        { id: product.id, payload: toUpdatePayload(values) },
        {
          onSuccess: () => {
            showToast('success', '상품을 수정했습니다.');
            navigate('/admin/products');
          },
          onError: handleError,
        },
      );
      return;
    }

    createMutation.mutate(toCreatePayload(values), {
      onSuccess: () => {
        showToast('success', '상품을 등록했습니다.');
        navigate('/admin/products');
      },
      onError: handleError,
    });
  });

  return (
    <form className="flex max-w-2xl flex-col gap-5" onSubmit={onSubmit} noValidate>
      <FormError message={errors.root?.serverError?.message} />

      <Field htmlFor="title" label="제목" required error={errors.title?.message}>
        <Input id="title" invalid={Boolean(errors.title)} {...register('title')} />
      </Field>

      <Field htmlFor="artistId" label="아티스트" required error={errors.artistId?.message}>
        <Controller
          control={control}
          name="artistId"
          render={({ field }) => (
            <ArtistSearchSelect
              id="artistId"
              value={field.value ? Number(field.value) : undefined}
              selectedName={product?.artist.name}
              invalid={Boolean(errors.artistId)}
              onChange={(artist) => field.onChange(artist ? String(artist.id) : '')}
            />
          )}
        />
      </Field>

      <Field htmlFor="labelId" label="레이블" error={errors.labelId?.message}>
        <Select id="labelId" invalid={Boolean(errors.labelId)} {...register('labelId')}>
          <option value="">없음</option>
          {labels?.map((label) => (
            <option key={label.id} value={label.id}>
              {label.name}
            </option>
          ))}
        </Select>
      </Field>

      <Field htmlFor="genreIds" label="장르" error={errors.genreIds?.message}>
        <Controller
          control={control}
          name="genreIds"
          render={({ field }) => (
            <GenreCheckboxGroup
              name="genreIds"
              value={field.value}
              genres={genres ?? []}
              onChange={field.onChange}
            />
          )}
        />
      </Field>

      <Field htmlFor="releaseDate" label="발매일" error={errors.releaseDate?.message}>
        <Input
          id="releaseDate"
          type="date"
          invalid={Boolean(errors.releaseDate)}
          {...register('releaseDate')}
        />
      </Field>

      <Field htmlFor="pressingInfo" label="프레싱" error={errors.pressingInfo?.message}>
        <Input id="pressingInfo" invalid={Boolean(errors.pressingInfo)} {...register('pressingInfo')} />
      </Field>

      <Field htmlFor="colorVariant" label="컬러반" error={errors.colorVariant?.message}>
        <Input id="colorVariant" invalid={Boolean(errors.colorVariant)} {...register('colorVariant')} />
      </Field>

      <Field htmlFor="price" label="가격" required error={errors.price?.message}>
        <Input
          id="price"
          inputMode="decimal"
          invalid={Boolean(errors.price)}
          {...register('price')}
        />
      </Field>

      <Field htmlFor="description" label="설명" error={errors.description?.message}>
        <Textarea
          id="description"
          rows={5}
          invalid={Boolean(errors.description)}
          {...register('description')}
        />
      </Field>

      <Field htmlFor="imageUrls" label="이미지" error={errors.imageUrls?.message}>
        <Controller
          control={control}
          name="imageUrls"
          render={({ field }) => (
            <ProductImageUploader id="imageUrls" value={field.value} onChange={field.onChange} />
          )}
        />
      </Field>

      {!isEdit && (
        <Field htmlFor="initialStock" label="초기 재고" required error={errors.initialStock?.message}>
          <Input
            id="initialStock"
            inputMode="numeric"
            invalid={Boolean(errors.initialStock)}
            {...register('initialStock')}
          />
        </Field>
      )}

      <div className="flex justify-end gap-2 pt-2">
        <Button
          type="button"
          variant="secondary"
          onClick={() => navigate('/admin/products')}
          disabled={isSubmitting || isMutating}
        >
          취소
        </Button>
        <Button type="submit" disabled={isSubmitting || isMutating}>
          {isEdit ? '수정' : '등록'}
        </Button>
      </div>
    </form>
  );
}
