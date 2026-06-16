package com.groove.common.api;

import org.springframework.data.domain.Sort;

/**
 * keyset(커서) 페이징의 정렬 결정성 보장 유틸 (#235).
 *
 * <p>keyset 스크롤은 정렬 튜플이 행을 <b>유일하게</b> 식별해야 누락/중복이 없다. 정렬 키가 비고유
 * 컬럼({@code createdAt} 등) 하나뿐이면 같은 값을 가진 행들의 페이지 경계 순서가 불안정해지므로,
 * PK({@code id}) 를 마지막 tiebreaker 로 덧붙여 전순서를 만든다. {@code id} 의 방향은 주 정렬 키의
 * 방향을 따라 인덱스 스캔 방향과 일관되게 한다.
 *
 * <p>{@code SortValidator} 화이트리스트 검증을 통과한 뒤 keyset 경로에서만 적용한다 — offset 경로는
 * 무관하므로 건드리지 않는다.
 */
public final class KeysetSort {

    private KeysetSort() {
    }

    public static Sort withIdTiebreaker(Sort sort) {
        boolean hasId = sort.stream().anyMatch(order -> "id".equals(order.getProperty()));
        if (hasId) {
            return sort;
        }
        Sort.Direction direction = sort.isSorted()
                ? sort.iterator().next().getDirection()
                : Sort.Direction.ASC;
        return sort.and(Sort.by(direction, "id"));
    }
}
