import { client, unwrap } from '@/api/client';
import type { ApiResponse } from '@/types/api';
import type { FileUploadResponse } from '@/types/file';

/* axios 가 FormData 를 보내면 boundary 를 자동으로 붙인다 - Content-Type 을 직접 지정하면 깨진다. */
export const uploadImage = (file: File) => {
  const formData = new FormData();
  formData.append('file', file);
  return unwrap(client.post<ApiResponse<FileUploadResponse>>('/files/images', formData));
};
