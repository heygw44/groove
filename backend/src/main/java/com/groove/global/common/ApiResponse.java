package com.groove.global.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 모든 API 응답의 공통 포맷.
 * 성공: { success: true, data, error: null, timestamp }
 * 실패: { success: false, data: null, error: { code, message, fieldErrors? }, timestamp }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorBody error,
        LocalDateTime timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, LocalDateTime.now());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, null), LocalDateTime.now());
    }

    public static <T> ApiResponse<T> error(String code, String message, List<FieldErrorBody> fieldErrors) {
        return new ApiResponse<>(false, null, new ErrorBody(code, message, fieldErrors), LocalDateTime.now());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(String code, String message, List<FieldErrorBody> fieldErrors) {
    }

    public record FieldErrorBody(String field, String reason) {
    }
}
