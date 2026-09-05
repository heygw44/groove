import { useRef, useState, type ChangeEvent, type DragEvent } from 'react';

import { Badge } from '@/components/common/Badge';
import { Spinner } from '@/components/common/Spinner';
import { useToast } from '@/components/common/toastContext';
import { useUploadImage } from '@/hooks/mutations/useFileMutations';
import { getErrorMessage } from '@/utils/apiError';

interface ProductImageUploaderProps {
  value: string[];
  onChange: (urls: string[]) => void;
  disabled?: boolean;
  id?: string;
}

const ACCEPTED_MIME_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);
const ACCEPTED_EXTENSIONS = /\.(jpe?g|png|webp)$/i;
const MAX_FILE_SIZE = 5 * 1024 * 1024;
const MAX_IMAGE_COUNT = 10;

const validateFile = (file: File): string | undefined => {
  if (!ACCEPTED_MIME_TYPES.has(file.type) || !ACCEPTED_EXTENSIONS.test(file.name)) {
    return `${file.name}: jpg, png, webp 형식만 업로드할 수 있습니다.`;
  }
  if (file.size > MAX_FILE_SIZE) {
    return `${file.name}: 파일 용량은 5MB를 넘을 수 없습니다.`;
  }
  return undefined;
};

export function ProductImageUploader({
  value,
  onChange,
  disabled = false,
  id,
}: ProductImageUploaderProps) {
  const { showToast } = useToast();
  const uploadMutation = useUploadImage();
  const inputRef = useRef<HTMLInputElement>(null);
  const [isDragOver, setIsDragOver] = useState(false);
  const [uploadingName, setUploadingName] = useState<string | undefined>(undefined);

  const isUploading = uploadingName !== undefined;

  const uploadFiles = async (files: File[]) => {
    const remainingSlots = MAX_IMAGE_COUNT - value.length;
    if (files.length > remainingSlots) {
      showToast('error', `이미지는 최대 ${MAX_IMAGE_COUNT}장까지 등록할 수 있습니다.`);
    }
    const targets = files.slice(0, Math.max(remainingSlots, 0));

    const uploaded: string[] = [];
    for (const file of targets) {
      const validationError = validateFile(file);
      if (validationError) {
        showToast('error', validationError);
        continue;
      }
      setUploadingName(file.name);
      try {
        const result = await uploadMutation.mutateAsync(file);
        uploaded.push(result.url);
      } catch (error) {
        showToast('error', getErrorMessage(error));
      }
    }
    setUploadingName(undefined);

    if (uploaded.length > 0) {
      onChange([...value, ...uploaded]);
    }
  };

  const handleFileInputChange = (event: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files ?? []);
    event.target.value = '';
    if (files.length > 0) {
      void uploadFiles(files);
    }
  };

  const handleDrop = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setIsDragOver(false);
    const files = Array.from(event.dataTransfer.files);
    if (files.length > 0) {
      void uploadFiles(files);
    }
  };

  const move = (index: number, direction: -1 | 1) => {
    const targetIndex = index + direction;
    if (targetIndex < 0 || targetIndex >= value.length) {
      return;
    }
    const next = [...value];
    [next[index], next[targetIndex]] = [next[targetIndex], next[index]];
    onChange(next);
  };

  const remove = (index: number) => {
    onChange(value.filter((_, i) => i !== index));
  };

  const isFull = value.length >= MAX_IMAGE_COUNT;

  return (
    <div>
      <div
        role="button"
        tabIndex={0}
        onClick={() => !disabled && !isFull && inputRef.current?.click()}
        onKeyDown={(event) => {
          if ((event.key === 'Enter' || event.key === ' ') && !disabled && !isFull) {
            inputRef.current?.click();
          }
        }}
        onDragOver={(event) => {
          event.preventDefault();
          setIsDragOver(true);
        }}
        onDragLeave={() => setIsDragOver(false)}
        onDrop={handleDrop}
        className={`flex h-28 flex-col items-center justify-center gap-1.5 rounded-md border border-dashed text-sm ${
          disabled || isFull
            ? 'cursor-not-allowed border-line text-content-subtle'
            : 'cursor-pointer text-content-muted hover:bg-surface-muted'
        } ${isDragOver ? 'border-content bg-surface-muted' : 'border-line-strong'}`}
      >
        {isUploading ? (
          <>
            <Spinner size="sm" />
            <span>{uploadingName} 업로드 중…</span>
          </>
        ) : (
          <span>
            {isFull
              ? `이미지는 최대 ${MAX_IMAGE_COUNT}장까지 등록할 수 있습니다.`
              : '클릭하거나 이미지를 끌어다 놓으세요 (jpg, png, webp / 5MB 이하)'}
          </span>
        )}
        <input
          ref={inputRef}
          id={id}
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          disabled={disabled || isFull}
          onChange={handleFileInputChange}
          className="hidden"
        />
      </div>

      {value.length > 0 && (
        <ul className="mt-3 grid grid-cols-3 gap-3 sm:grid-cols-4">
          {value.map((url, index) => (
            <li key={url} className="relative">
              <img src={url} alt="" className="aspect-square w-full rounded-md object-cover" />
              {index === 0 && (
                <Badge variant="accent" className="absolute left-1.5 top-1.5">
                  대표
                </Badge>
              )}
              <div className="absolute bottom-1.5 right-1.5 flex gap-1">
                <button
                  type="button"
                  onClick={() => move(index, -1)}
                  disabled={disabled || index === 0}
                  aria-label="앞으로 이동"
                  className="flex h-6 w-6 items-center justify-center rounded-full bg-content/70 text-xs text-surface disabled:cursor-not-allowed disabled:opacity-40"
                >
                  ↑
                </button>
                <button
                  type="button"
                  onClick={() => move(index, 1)}
                  disabled={disabled || index === value.length - 1}
                  aria-label="뒤로 이동"
                  className="flex h-6 w-6 items-center justify-center rounded-full bg-content/70 text-xs text-surface disabled:cursor-not-allowed disabled:opacity-40"
                >
                  ↓
                </button>
                <button
                  type="button"
                  onClick={() => remove(index)}
                  disabled={disabled}
                  aria-label="삭제"
                  className="flex h-6 w-6 items-center justify-center rounded-full bg-content/70 text-xs text-surface disabled:cursor-not-allowed disabled:opacity-40"
                >
                  ×
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
