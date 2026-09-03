import { useMutation } from '@tanstack/react-query';

import { uploadImage } from '@/api/file';

export const useUploadImage = () =>
  useMutation({
    mutationFn: (file: File) => uploadImage(file),
  });
