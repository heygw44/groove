package com.groove.common.api;

import com.groove.common.exception.ErrorCode;
import com.groove.common.exception.ValidationException;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * 정렬 키 화이트리스트 검증 — sort 가 허용 컬럼만 참조하는지 확인한다.
 * 화이트리스트에 없는 컬럼이 발견되면 ValidationException(400 VALIDATION_FAILED)을 던진다.
 */
public final class SortValidator {

    private SortValidator() {
    }

    public static void requireAllowed(Sort sort, Set<String> allowedProperties) {
        for (Sort.Order order : sort) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new ValidationException(
                        ErrorCode.VALIDATION_FAILED,
                        "허용되지 않는 정렬 키: " + order.getProperty());
            }
        }
    }
}
