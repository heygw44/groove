package com.groove.order.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@link JpaSpecificationExecutor} 확장: 관리자 주문 목록의 동적 필터(상태/회원/기간 조합) 를
 * {@link OrderSpecifications} 조각으로 표현하기 위함이다 (이슈 #69). 회원/게스트 측 조회는 기존 derived
 * method 를 그대로 쓴다 — Specification 은 admin 경로 전용.
 */
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

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

    /**
     * 회원 탈퇴 차단 검사 (#78) — 주어진 상태 집합에 해당하는 회원 주문의 존재 여부.
     *
     * <p>{@code MemberService.withdraw} 가 "진행 중" 으로 보는 {@code {PAID, PREPARING, SHIPPED}} 으로
     * 호출한다. 하나라도 있으면 탈퇴를 막아({@code MEMBER_WITHDRAWAL_BLOCKED} 409) 배송·환불 책임
     * 주체가 사라지는 것을 방지한다. PENDING(미결제)·종착 상태(DELIVERED/COMPLETED/CANCELLED/PAYMENT_FAILED)
     * 는 차단 대상이 아니다.
     */
    boolean existsByMemberIdAndStatusIn(Long memberId, Collection<OrderStatus> statuses);

    /**
     * 내 쿠폰함(#137) 의 사용 완료 쿠폰 → 주문번호 일괄 resolve 전용 경량 프로젝션.
     *
     * <p>{@code MemberCoupon.orderId} 집합으로 {@code (id, orderNumber)} 두 컬럼만 IN 조회해
     * N+1 과 {@link Order#getItems items} 그래프 적재를 동시에 회피한다.
     */
    List<OrderNumberView> findByIdIn(Collection<Long> ids);

    interface OrderNumberView {
        Long getId();

        String getOrderNumber();
    }
}
