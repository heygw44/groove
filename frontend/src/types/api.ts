export interface FieldError {
  field: string;
  reason: string;
}

export interface ApiError {
  code: string;
  message: string;
  fieldErrors?: FieldError[];
}

/**
 * 백엔드 공통 응답. @JsonInclude(NON_NULL) 이라 성공 응답에는 error 키가,
 * 반환값 없는 API 응답에는 data 키가 아예 없다.
 */
export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: ApiError;
  timestamp: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
