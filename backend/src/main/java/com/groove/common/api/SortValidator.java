package com.groove.common.api;

import com.groove.common.exception.ErrorCode;
import com.groove.common.exception.ValidationException;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * 정렬 키 화이트리스트 검증 — {@link Pageable} 의 {@code sort} 가 허용 컬럼만 참조하는지 확인한다.
 *
 * <p>임의 컬럼 정렬을 그대로 통과시키면 인덱스 없는 컬럼에 대한 비정렬·풀스캔 정렬이 노출되거나 (LIKE
 * 검색을 회피한 사이드 채널 공격에 가까운) 도메인 노출이 일어날 수 있어, 페이징 API 는 모두 도메인별
 * 화이트리스트로 막는다 — 본 메서드가 그 단일 지점이다 (이전엔 {@code MemberOrderController},
 * {@code AlbumQueryController}, {@code AlbumReviewController}, {@code AdminOrderController}
 * 4곳에 중복 구현되어 있었음, CodeRabbit 리뷰 반영).
 *
 * @throws ValidationException 화이트리스트에 없는 컬럼이 발견되면 400 {@code VALIDATION_FAILED}.
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
