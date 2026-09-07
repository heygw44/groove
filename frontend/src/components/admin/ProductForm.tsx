import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';

import { AlbumSearchSelect } from '@/components/admin/AlbumSearchSelect';
import { DiscogsImportModal } from '@/components/admin/catalog/DiscogsImportModal';
import { GenreCheckboxGroup } from '@/components/admin/GenreCheckboxGroup';
import { ProductImageUploader } from '@/components/admin/ProductImageUploader';
import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Input } from '@/components/common/Input';
import { Select } from '@/components/common/Select';
import { Spinner } from '@/components/common/Spinner';
import { Textarea } from '@/components/common/Textarea';
import { useToast } from '@/components/common/toastContext';
import { ArtistSearchSelect } from '@/components/product/ArtistSearchSelect';
import {
  EDITION_TYPE_LABELS,
  PRESSING_COUNTRIES,
  PRESSING_COUNTRY_LABELS,
} from '@/constants/product';
import { useCreateProduct, useUpdateProduct } from '@/hooks/mutations/useAdminProductMutations';
import { useGenres, useLabels } from '@/hooks/queries/useReferences';
import {
  EMPTY_PRODUCT_FORM_VALUES,
  productCreateSchema,
  productFormSchema,
  toCreatePayload,
  toFormValues,
  toFormValuesFromRelease,
  toUpdatePayload,
  type ProductFormValues,
} from '@/schemas/product';
import type { CatalogReleaseDetail } from '@/types/catalog';
import type { Genre, Label, ProductFormSource } from '@/types/product';
import { applyFieldErrors, getErrorMessage } from '@/utils/apiError';

interface ProductFormProps {
  /** 있으면 수정, 없으면 등록. 페이지가 등록/수정으로 분리되어 있어 이 값은 마운트 중 바뀌지 않는다. */
  product?: ProductFormSource;
}

interface ProductFormBodyProps extends ProductFormProps {
  genres: Genre[];
  labels: Label[];
}

export function ProductForm({ product }: ProductFormProps) {
  const { data: genres } = useGenres();
  const { data: labels } = useLabels();

  // register 기반 셀렉트는 기본값을 마운트 시점에 한 번만 적용한다. 옵션이 나중에 오면 레이블이 "없음"으로
  // 보이고, 그대로 저장하면 해제 요청(null)이 나가므로 기준 데이터가 준비된 뒤에 폼을 그린다.
  if (!genres || !labels) {
    return <Spinner />;
  }

  return <ProductFormBody product={product} genres={genres} labels={labels} />;
}

function ProductFormBody({ product, genres, labels }: ProductFormBodyProps) {
  const isEdit = Boolean(product);
  const navigate = useNavigate();
  const { showToast } = useToast();
  const createMutation = useCreateProduct();
  const updateMutation = useUpdateProduct();
  const [discogsModalOpen, setDiscogsModalOpen] = useState(false);
  // Discogs 프리필로 아티스트 이름 검색을 미리 시작해준다. ArtistSearchSelect 는 마운트 시점 값만
  // 반영하므로, 값이 바뀔 때마다 key 로 리마운트시켜 검색창에 다시 채운다.
  const [artistKeywordSeed, setArtistKeywordSeed] = useState('');

  const {
    register,
    handleSubmit,
    control,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ProductFormValues>({
    resolver: zodResolver(product ? productFormSchema : productCreateSchema),
    mode: 'onBlur',
    defaultValues: product ? toFormValues(product) : EMPTY_PRODUCT_FORM_VALUES,
  });

  const albumMode = useWatch({ control, name: 'albumMode' });
  const isMutating = createMutation.isPending || updateMutation.isPending;

  const handleError = (error: unknown) => {
    if (!applyFieldErrors(error, setError)) {
      setError('root.serverError', { message: getErrorMessage(error) });
    }
  };

  const handleDiscogsImport = (detail: CatalogReleaseDetail) => {
    const mapped = toFormValuesFromRelease(detail, labels);
    setValue('title', mapped.title, { shouldDirty: true, shouldValidate: true });
    setValue('labelId', mapped.labelId, { shouldDirty: true, shouldValidate: true });
    setValue('country', mapped.country, { shouldDirty: true, shouldValidate: true });
    setValue('pressingYear', mapped.pressingYear, { shouldDirty: true, shouldValidate: true });
    setValue('catalogNo', mapped.catalogNo, { shouldDirty: true, shouldValidate: true });
    setValue('barcode', mapped.barcode, { shouldDirty: true, shouldValidate: true });
    setValue('editionType', mapped.editionType, { shouldDirty: true, shouldValidate: true });
    setValue('description', mapped.description, { shouldDirty: true, shouldValidate: true });
    setValue('albumMode', mapped.albumMode, { shouldDirty: true, shouldValidate: true });
    setValue('albumId', '', { shouldDirty: true, shouldValidate: true });
    setValue('newAlbumTitle', mapped.newAlbumTitle, { shouldDirty: true, shouldValidate: true });
    setValue('newAlbumYear', mapped.newAlbumYear, { shouldDirty: true, shouldValidate: true });
    setArtistKeywordSeed(detail.artistName ?? '');
    setDiscogsModalOpen(false);
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

      {!isEdit && (
        <div className="flex justify-end">
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={() => setDiscogsModalOpen(true)}
          >
            Discogs 에서 불러오기
          </Button>
        </div>
      )}

      <Field htmlFor="title" label="제목" required error={errors.title?.message}>
        <Input id="title" invalid={Boolean(errors.title)} {...register('title')} />
      </Field>

      <Field htmlFor="artistId" label="아티스트" required error={errors.artistId?.message}>
        <Controller
          control={control}
          name="artistId"
          render={({ field }) => (
            <ArtistSearchSelect
              key={artistKeywordSeed}
              id="artistId"
              value={field.value ? Number(field.value) : undefined}
              selectedName={product?.artist.name}
              initialKeyword={artistKeywordSeed}
              invalid={Boolean(errors.artistId)}
              onChange={(artist) => field.onChange(artist ? String(artist.id) : '')}
            />
          )}
        />
      </Field>

      <Field htmlFor="labelId" label="레이블" error={errors.labelId?.message}>
        <Select id="labelId" invalid={Boolean(errors.labelId)} {...register('labelId')}>
          <option value="">없음</option>
          {labels.map((label) => (
            <option key={label.id} value={label.id}>
              {label.name}
            </option>
          ))}
        </Select>
      </Field>

      {!isEdit && (
        <div className="flex flex-col gap-3 rounded-md border border-line p-4">
          <span className="text-sm font-medium text-content">앨범</span>

          <Controller
            control={control}
            name="albumMode"
            render={({ field }) => (
              <div className="flex gap-4 text-sm">
                <label className="flex items-center gap-2">
                  <input
                    type="radio"
                    name={field.name}
                    value="existing"
                    checked={field.value === 'existing'}
                    onChange={() => field.onChange('existing')}
                    className="h-4 w-4 accent-content"
                  />
                  기존 앨범
                </label>
                <label className="flex items-center gap-2">
                  <input
                    type="radio"
                    name={field.name}
                    value="new"
                    checked={field.value === 'new'}
                    onChange={() => field.onChange('new')}
                    className="h-4 w-4 accent-content"
                  />
                  새 앨범
                </label>
              </div>
            )}
          />

          {albumMode === 'existing' ? (
            <Field htmlFor="albumId" label="앨범 검색" required error={errors.albumId?.message}>
              <Controller
                control={control}
                name="albumId"
                render={({ field }) => (
                  <AlbumSearchSelect
                    id="albumId"
                    value={field.value ? Number(field.value) : undefined}
                    invalid={Boolean(errors.albumId)}
                    onChange={(album) => field.onChange(album ? String(album.id) : '')}
                  />
                )}
              />
            </Field>
          ) : (
            <div className="flex flex-col gap-3">
              <Field
                htmlFor="newAlbumTitle"
                label="새 앨범 제목"
                required
                error={errors.newAlbumTitle?.message}
              >
                <Input
                  id="newAlbumTitle"
                  invalid={Boolean(errors.newAlbumTitle)}
                  {...register('newAlbumTitle')}
                />
              </Field>
              <Field htmlFor="newAlbumYear" label="발매 연도" error={errors.newAlbumYear?.message}>
                <Input
                  id="newAlbumYear"
                  inputMode="numeric"
                  invalid={Boolean(errors.newAlbumYear)}
                  {...register('newAlbumYear')}
                />
              </Field>
            </div>
          )}
        </div>
      )}

      {isEdit && product && (
        <Field htmlFor="albumInfo" label="앨범">
          <p id="albumInfo" className="text-sm text-content-muted">
            {product.album.title}
            {product.album.originalReleaseYear ? ` (${product.album.originalReleaseYear})` : ''}
          </p>
        </Field>
      )}

      <Field htmlFor="genreIds" label="장르" error={errors.genreIds?.message}>
        <Controller
          control={control}
          name="genreIds"
          render={({ field }) => (
            <GenreCheckboxGroup
              name="genreIds"
              value={field.value}
              genres={genres}
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

      <Field htmlFor="pressingInfo" label="사양" error={errors.pressingInfo?.message}>
        <Input
          id="pressingInfo"
          invalid={Boolean(errors.pressingInfo)}
          {...register('pressingInfo')}
        />
      </Field>

      <Field htmlFor="colorVariant" label="컬러반" error={errors.colorVariant?.message}>
        <Input
          id="colorVariant"
          invalid={Boolean(errors.colorVariant)}
          {...register('colorVariant')}
        />
      </Field>

      <Field htmlFor="country" label="제작 국가" error={errors.country?.message}>
        <Select id="country" invalid={Boolean(errors.country)} {...register('country')}>
          <option value="">없음</option>
          {PRESSING_COUNTRIES.map((country) => (
            <option key={country} value={country}>
              {PRESSING_COUNTRY_LABELS[country]}
            </option>
          ))}
        </Select>
      </Field>

      <Field htmlFor="pressingYear" label="제작 연도" error={errors.pressingYear?.message}>
        <Input
          id="pressingYear"
          inputMode="numeric"
          invalid={Boolean(errors.pressingYear)}
          {...register('pressingYear')}
        />
      </Field>

      <Field htmlFor="catalogNo" label="카탈로그 번호" error={errors.catalogNo?.message}>
        <Input id="catalogNo" invalid={Boolean(errors.catalogNo)} {...register('catalogNo')} />
      </Field>

      <Field htmlFor="barcode" label="바코드" error={errors.barcode?.message}>
        <Input id="barcode" invalid={Boolean(errors.barcode)} {...register('barcode')} />
      </Field>

      <Field htmlFor="editionType" label="에디션" error={errors.editionType?.message}>
        <Select id="editionType" invalid={Boolean(errors.editionType)} {...register('editionType')}>
          <option value="">선택 안 함</option>
          {Object.entries(EDITION_TYPE_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
      </Field>

      <Field htmlFor="price" label="가격" required error={errors.price?.message}>
        <Input
          id="price"
          inputMode="numeric"
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
        <Field
          htmlFor="initialStock"
          label="초기 재고"
          required
          error={errors.initialStock?.message}
        >
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

      {!isEdit && discogsModalOpen && (
        <DiscogsImportModal
          onClose={() => setDiscogsModalOpen(false)}
          onImport={handleDiscogsImport}
        />
      )}
    </form>
  );
}
