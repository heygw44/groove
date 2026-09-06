import { zodResolver } from '@hookform/resolvers/zod';
import { Controller, useForm } from 'react-hook-form';

import { Button } from '@/components/common/Button';
import { Field } from '@/components/common/Field';
import { FormError } from '@/components/common/FormError';
import { Skeleton } from '@/components/common/Skeleton';
import { useToast } from '@/components/common/toastContext';
import { ArtistChipPicker } from '@/components/recommend/ArtistChipPicker';
import { DecadeToggleGroup } from '@/components/recommend/DecadeToggleGroup';
import { GenreChipGroup } from '@/components/recommend/GenreChipGroup';
import { TASTE_ARTIST_MAX, TASTE_DECADE_MAX, TASTE_GENRE_MAX } from '@/constants/taste';
import { useUpdateTasteProfile } from '@/hooks/mutations/useTasteProfileMutations';
import { useGenres } from '@/hooks/queries/useReferences';
import {
  EMPTY_TASTE_FORM_VALUES,
  tasteProfileFormSchema,
  toTasteFormValues,
  toTastePayload,
  type TasteProfileFormValues,
} from '@/schemas/recommend';
import type { TasteProfile } from '@/types/recommend';
import { applyFieldErrors, getErrorMessage } from '@/utils/apiError';
import { getArrayFieldErrorMessage } from '@/utils/formErrors';

interface TasteProfileFormProps {
  profile?: TasteProfile | null;
  onSaved?: () => void;
  submitLabel?: string;
}

export function TasteProfileForm({ profile, onSaved, submitLabel = '저장' }: TasteProfileFormProps) {
  const { showToast } = useToast();
  const { data: genres, isPending: isGenresPending } = useGenres();
  const { mutate, isPending } = useUpdateTasteProfile();

  const {
    control,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<TasteProfileFormValues>({
    resolver: zodResolver(tasteProfileFormSchema),
    mode: 'onSubmit',
    values: profile ? toTasteFormValues(profile) : EMPTY_TASTE_FORM_VALUES,
  });

  const submit = handleSubmit((values) => {
    mutate(toTastePayload(values), {
      onSuccess: () => {
        showToast('success', '취향을 저장했어요');
        onSaved?.();
      },
      onError: (error) => {
        if (!applyFieldErrors(error, setError)) {
          setError('root.serverError', { message: getErrorMessage(error) });
        }
      },
    });
  });

  if (isGenresPending) {
    return (
      <div className="flex flex-col gap-3">
        <Skeleton className="h-9 w-full" />
        <Skeleton className="h-9 w-full" />
        <Skeleton className="h-9 w-full" />
      </div>
    );
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={submit} noValidate>
      <FormError message={errors.root?.serverError?.message} />

      <Field htmlFor="taste-genre" label="장르" error={errors.genreIds?.message}>
        <Controller
          control={control}
          name="genreIds"
          render={({ field }) => (
            <GenreChipGroup
              value={field.value}
              onChange={field.onChange}
              genres={genres ?? []}
              max={TASTE_GENRE_MAX}
              invalid={Boolean(errors.genreIds)}
            />
          )}
        />
      </Field>

      <Field htmlFor="taste-artist" label="아티스트" error={getArrayFieldErrorMessage(errors.artists)}>
        <Controller
          control={control}
          name="artists"
          render={({ field }) => (
            <ArtistChipPicker
              id="taste-artist"
              value={field.value}
              onChange={field.onChange}
              max={TASTE_ARTIST_MAX}
              invalid={Boolean(errors.artists)}
            />
          )}
        />
      </Field>

      <Field htmlFor="taste-decade" label="연대" error={errors.decades?.message}>
        <Controller
          control={control}
          name="decades"
          render={({ field }) => (
            <DecadeToggleGroup
              value={field.value}
              onChange={field.onChange}
              max={TASTE_DECADE_MAX}
              invalid={Boolean(errors.decades)}
            />
          )}
        />
      </Field>

      <Button type="submit" disabled={isPending}>
        {submitLabel}
      </Button>
    </form>
  );
}
