package com.groove.order.domain;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
     * 배송 reconciliation 안전망(#169) 전용 — PAID 가 된 지 일정 시간 지났는데 아직 배송이 없는 고아 주문을
     * {@code paid_at} 오름차순(오래된 것 먼저)으로 찾는다. 보충에는 {@code (id, orderNumber)} 만 필요하므로 경량
     * {@link OrderNumberView} 프로젝션으로 받아 full 엔티티 적재를 피한다 — 실제 배송 생성은
     * {@code ShippingProvisioner} 가 id 로 관리 상태 주문을 재로딩해 수행한다.
     *
     * <p>{@code paid_at} 은 {@link Order#changeStatus} 가 PAID 진입 시 찍는 시각이라 "결제 완료 후 경과"를 정확히
     * 잰다 — {@code cutoff = now - min-age} 로 갓 결제돼 리스너가 곧 처리할 건은 제외한다. 정상 흐름에선 배송 생성과
     * 함께 주문이 PREPARING 으로 전진하므로 PAID 잔류 집합은 과도 상태로 항상 소량이다(orders 상태 인덱스는
     * 슬로우 쿼리 측정 후 W10 으로 연기). 오름차순 정렬로 적체 시 오래된 고아부터 공정하게 보충하고, {@code limit}
     * 으로 한 주기 처리량을 제한한다.
     */
    List<OrderNumberView> findByStatusAndPaidAtBeforeOrderByPaidAtAsc(OrderStatus status, Instant cutoff, Limit limit);

    /**
     * 앨범 삭제 차단 검사 (#159) — 해당 album 을 참조하는 order_item 존재 여부.
     *
     * <p>{@code order_item.album_id} 는 ON DELETE RESTRICT FK 다. {@code AlbumService.delete} 가
     * 사전 검사로 호출해 {@code ALBUM_IN_USE}(409) 를 반환하고, 동시 INSERT race 는 DB FK 가 최종 방어한다.
     */
    @Query("SELECT CASE WHEN COUNT(oi) > 0 THEN true ELSE false END FROM OrderItem oi WHERE oi.album.id = :albumId")
    boolean existsByAlbumId(@Param("albumId") Long albumId);

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
