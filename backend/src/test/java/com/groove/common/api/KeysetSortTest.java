package com.groove.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KeysetSort — id tiebreaker 보강")
class KeysetSortTest {

    @Test
    @DisplayName("id 없는 단일 정렬 → 주 정렬 방향을 따르는 id 가 마지막에 추가된다")
    void appendsIdFollowingPrimaryDirection() {
        Sort result = KeysetSort.withIdTiebreaker(Sort.by(Sort.Direction.DESC, "createdAt"));

        List<Sort.Order> orders = result.toList();
        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getProperty()).isEqualTo("createdAt");
        assertThat(orders.get(0).getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(orders.get(1).getProperty()).isEqualTo("id");
        assertThat(orders.get(1).getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("ASC 정렬 → id 도 ASC 로 붙는다")
    void appendsAscIdForAscSort() {
        Sort result = KeysetSort.withIdTiebreaker(Sort.by(Sort.Direction.ASC, "price"));

        assertThat(result.toList()).hasSize(2);
        assertThat(result.getOrderFor("id")).isNotNull();
        assertThat(result.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("이미 id 가 포함된 정렬 → 그대로 유지(중복 추가 안 함)")
    void leavesSortWithIdUntouched() {
        Sort original = Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));

        Sort result = KeysetSort.withIdTiebreaker(original);

        assertThat(result).isEqualTo(original);
        assertThat(result.toList()).hasSize(2);
    }

    @Test
    @DisplayName("정렬 미지정 → id ASC 단일 정렬")
    void unsortedGetsIdAsc() {
        Sort result = KeysetSort.withIdTiebreaker(Sort.unsorted());

        assertThat(result.toList()).hasSize(1);
        assertThat(result.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
    }
}
