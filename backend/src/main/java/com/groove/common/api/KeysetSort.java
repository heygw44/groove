package com.groove.common.api;

import org.springframework.data.domain.Sort;

/**
 * keyset(커서) 페이징 정렬에 PK(id)를 마지막 tiebreaker 로 덧붙여 전순서를 만드는 유틸.
 * id 의 방향은 주 정렬 키의 방향을 따른다.
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
