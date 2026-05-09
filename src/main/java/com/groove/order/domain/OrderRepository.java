package com.groove.order.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * 단건 조회 (회원 GET / 게스트 lookup) — items 까지 fetch 한다.
     *
     * <p>{@code OrderItemResponse} 는 album LAZY 프록시의 {@code id} (DB hit 없음) 와
     * {@code albumTitleSnapshot} (OrderItem 자체 컬럼) 만 사용하므로 album 행 join 은 불필요.
     * 취소 흐름처럼 album 본체를 변경해야 할 때는 {@link #findWithAlbumsByOrderNumber} 를 쓴다.
     */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findByOrderNumber(String orderNumber);

    /**
     * 취소 흐름 전용 — items + items.album 까지 한 번에 fetch 한다.
     *
     * <p>{@link com.groove.order.application.OrderService#cancel} 는 OrderItem 마다
     * {@code album.adjustStock(+qty)} 를 호출하므로 album 본체가 영속 컨텍스트에 미리
     * 로드돼야 N+1 회피가 가능하다.
     */
    @EntityGraph(attributePaths = {"items", "items.album"})
    Optional<Order> findWithAlbumsByOrderNumber(String orderNumber);

    boolean existsByOrderNumber(String orderNumber);

    /**
     * 회원 주문 목록 (페이징).
     *
     * <p>{@code @EntityGraph(items)} 를 두지 않는 이유: 컬렉션 fetch join + {@code Pageable} 조합은
     * Hibernate 가 SQL LIMIT 을 적용하지 못해 모든 행을 메모리에 적재한 뒤 자바 레벨에서 페이지네이션을
     * 수행한다 (HHH90003004 경고). {@link Order#getItems items} 컬렉션의 {@code @BatchSize} 가
     * Order 페이지 후 IN 쿼리로 일괄 로드해 N+1 과 인메모리 페이지네이션을 동시에 회피한다.
     */
    Page<Order> findByMemberId(Long memberId, Pageable pageable);

    Page<Order> findByMemberIdAndStatus(Long memberId, OrderStatus status, Pageable pageable);
}
