package com.groove.order.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.album.domain.StockRestorer;
import com.groove.catalog.album.exception.AlbumNotFoundException;
import com.groove.cart.exception.AlbumNotPurchasableException;
import com.groove.coupon.application.CouponApplicationService;
import com.groove.coupon.exception.CouponNotApplicableException;
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberNotFoundException;
import com.groove.order.api.dto.GuestInfoRequest;
import com.groove.order.api.dto.OrderCreateRequest;
import com.groove.order.api.dto.OrderItemRequest;
import com.groove.order.api.dto.ShippingInfoRequest;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderShippingInfo;
import com.groove.order.domain.OrderSpecifications;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.IllegalStateTransitionException;
import com.groove.order.exception.InsufficientStockException;
import com.groove.order.exception.InvalidOrderOwnershipException;
import com.groove.order.exception.OrderNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 주문 생성 트랜잭션 경계 (#43, API.md §3.5).
 *
 * 재고 차감은 비관적 락(SELECT ... FOR UPDATE)으로 직렬화해 동시 주문 간 lost-update/oversell 을 막는다
 * (#205, W6-6 에서 의도적으로 노출했던 결함 해소). 다중 album 은 albumId 오름차순으로 락을 잡아 deadlock 을
 * 피한다. 락 없는 baseline 재현은 placeWithoutLock(테스트 전용)으로 보존한다.
 *
 * 쿠폰 적용 실패는 같은 트랜잭션 롤백으로 재고/주문까지 되돌린다(#91). cancel 은 재고 복원 직후 쿠폰을
 * USED→ISSUED 로 되살려 취소·환불 양 경로의 복원 누락을 막는다(#91).
 *
 * 재고 복원 경로(취소·환불·결제실패 보상·반품 재입고)는 원자적 가산 UPDATE(AlbumRepository.restoreStock, #234)로,
 * admin 단건 재고조정은 비관락(findByIdForUpdate, #205 재사용)으로 lost-update 를 제거했다 — place 핫경로와 대칭.
 * 상세 docs/improvements/concurrency.md §7 (트레이드오프: 비관 vs 낙관 vs 원자적).
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final int MAX_ORDER_NUMBER_ATTEMPTS = 3;

    private final OrderRepository orderRepository;
    private final AlbumRepository albumRepository;
    private final OrderNumberGenerator orderNumberGenerator;
    private final CouponApplicationService couponApplicationService;
    private final MemberRepository memberRepository;

    public OrderService(OrderRepository orderRepository,
                        AlbumRepository albumRepository,
                        OrderNumberGenerator orderNumberGenerator,
                        CouponApplicationService couponApplicationService,
                        MemberRepository memberRepository) {
        this.orderRepository = orderRepository;
        this.albumRepository = albumRepository;
        this.orderNumberGenerator = orderNumberGenerator;
        this.couponApplicationService = couponApplicationService;
        this.memberRepository = memberRepository;
    }

    /**
     * 회원 본인 주문 단건 조회 (API.md §3.5).
     * 타 회원/게스트 주문은 존재 노출 회피로 404 통일.
     */
    @Transactional(readOnly = true)
    public Order findForMember(Long memberId, String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(OrderNotFoundException::new);
        if (order.isGuestOrder() || !order.getMemberId().equals(memberId)) {
            throw new OrderNotFoundException();
        }
        return order;
    }

    /**
     * 게스트 주문 단건 조회 (API.md §3.5).
     * email 매칭 실패·회원 주문에 게스트 접근은 모두 404 통일.
     *
     * PII 익명화(#170 Part B)로 guestEmail 이 NULL 이 된 주문(배송완료 후 보존기간 경과)은 매칭할 평문이
     * 없으므로 404 통일 — null 가드 없이 equalsIgnoreCase 호출 시 NPE(500).
     */
    @Transactional(readOnly = true)
    public Order findForGuest(String orderNumber, String email) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(OrderNotFoundException::new);
        if (!order.isGuestOrder() || order.getGuestEmail() == null
                || !order.getGuestEmail().equalsIgnoreCase(email)) {
            throw new OrderNotFoundException();
        }
        return order;
    }

    /**
     * 회원 주문 목록 조회 (API.md §3.5).
     *
     * status null 이면 전체, 지정 시 필터. IS NULL OR ... JPQL 대신 derived method 2종 분기로 처리해
     * 옵티마이저가 단순 인덱스 스캔을 택하기 쉽게 한다.
     *
     * 페이지 쿼리는 컬렉션 fetch join 없이(items @BatchSize 활용) 트랜잭션 안에서 items 를 강제 초기화한다 —
     * 컨트롤러 응답 매핑(SUMMARY DTO) 시점의 LazyInitializationException 회피.
     */
    @Transactional(readOnly = true)
    public Page<Order> listForMember(Long memberId, OrderStatus status, Pageable pageable) {
        Page<Order> page = (status == null)
                ? orderRepository.findByMemberId(memberId, pageable)
                : orderRepository.findByMemberIdAndStatus(memberId, status, pageable);
        page.forEach(order -> order.getItems().size());
        return page;
    }

    /**
     * 회원 주문 목록 — keyset(커서) 페이징 변형 (#235, GET /members/me/orders/scroll).
     *
     * <p>offset {@link #listForMember} 와 동일하게 회원 본인 주문만 조회하되, Spring Data Scroll API
     * ({@code findBy(...).scroll(position)})로 {@link Window} 를 반환한다 — 깊은 페이지에서 {@code OFFSET N}
     * 스캔 없이 {@code idx_orders_member_created (member_id, created_at)}(V22)를 타고 keyset 으로 전진한다.
     * {@code member_id} 는 인증 주체에서 받은 값으로만 스코프하고, 정렬은 호출 측이 {@code id} tiebreaker
     * 까지 포함해 넘긴다(전순서 보장).
     *
     * <p>status 는 있을 때만 조건에 더해 {@code IS NULL OR} 없이 단순 인덱스 스캔을 유도한다(offset 의
     * derived method 2종 분기와 동일 의도). items 컬렉션은 offset 경로와 동일하게 트랜잭션 안에서
     * {@code @BatchSize(50)} 일괄 로드로 강제 초기화해, 컨트롤러 DTO 매핑 시 LazyInitializationException 을 피한다.
     */
    @Transactional(readOnly = true)
    public Window<Order> listForMemberKeyset(Long memberId, OrderStatus status, int size, Sort sort, ScrollPosition position) {
        Specification<Order> spec = OrderSpecifications.hasMemberId(memberId);
        if (status != null) {
            spec = spec.and(OrderSpecifications.hasStatus(status));
        }
        Window<Order> window = orderRepository.findBy(spec, query -> query.sortBy(sort).limit(size).scroll(position));
        window.forEach(order -> order.getItems().size());
        return window;
    }

    /**
     * 회원 본인 주문 취소 (API.md §3.5, #44).
     *
     * PENDING 한정 — PAID/PREPARING 취소는 환불 흐름이 필요해 W7 결제 도메인에서 다룬다.
     * canTransitionTo 가 PAID→CANCELLED 를 허용해도 서비스 레벨에서 PENDING 만 통과시킨다(이중 방어선).
     *
     * 재고 복원은 ApplicationService 에서 조율한다(place 와 대칭) — Order 와 Album 은 다른 Aggregate 라
     * 도메인 메서드 안에서 다른 Aggregate 를 변경하지 않는다.
     */
    @Transactional
    public Order cancel(Long memberId, String orderNumber, String reason) {
        Order order = orderRepository.findWithAlbumsByOrderNumber(orderNumber)
                .orElseThrow(OrderNotFoundException::new);
        if (order.isGuestOrder() || !order.getMemberId().equals(memberId)) {
            throw new OrderNotFoundException();
        }
        // 토큰 유효기간 내 탈퇴(soft delete)한 회원이면 404 로 차단한다 (#187, place 와 일관) — cancel 은 member 전용.
        // PENDING 은 탈퇴 차단 상태가 아니므로 쿠폰 적용 주문을 가진 회원이 탈퇴 후 취소를 시도할 수 있는데,
        // restoreForOrder 가 쿠폰을 USED→ISSUED 로 되살려 익명화된 탈퇴회원에 사용가능 쿠폰이 재부착되는 비정합을 막는다.
        if (!memberRepository.existsByIdAndDeletedAtIsNull(memberId)) {
            throw new MemberNotFoundException();
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateTransitionException(order.getStatus(), OrderStatus.CANCELLED);
        }
        order.changeStatus(OrderStatus.CANCELLED, reason);
        // 배송 취소 동기화(#233)는 여기서 불필요하다 — 배송은 결제 완료(PAID) 후에야 생성되는데 cancel 은 PENDING 한정이라
        // 취소 시점에 배송 행이 존재하지 않는다. 발송 전(PAID/PREPARING) 취소·환불의 배송 동기화는 refund 경로가 담당한다.
        // 재고 복원 — 원자적 가산 UPDATE 로 place(FOR UPDATE)·동시 복원과의 lost-update 창을 없앤다 (#234).
        // adjustStock(in-memory) 대신 restoreStock 를 써야 stale 절대값 dirty-check 가 증분을 덮어쓰지 않는다.
        // StockRestorer 가 albumId 오름차순으로 정렬해 place 와 같은 락 순서로 다중 album 데드락을 피한다.
        StockRestorer.restore(albumRepository, order.getItems().stream()
                .collect(Collectors.groupingBy(item -> item.getAlbum().getId(), Collectors.summingInt(OrderItem::getQuantity))));
        // 쿠폰 적용된 주문이면 USED→ISSUED 복원 (이슈 #91 DoD HIGH 리스크: 양 경로 모두 복원).
        // 미적용 주문은 no-op 이므로 분기 없이 안전하게 호출한다.
        couponApplicationService.restoreForOrder(order.getId());
        return order;
    }

    /**
     * 주문 생성 — 재고 차감에 비관적 락(SELECT ... FOR UPDATE)을 적용해 lost-update/oversell 을 차단한다
     * (#205). 프로덕션 진입점.
     */
    @Transactional
    public Order place(Long memberId, OrderCreateRequest request) {
        return placeInternal(memberId, request, true);
    }

    /**
     * 락 미적용 주문 생성 — 테스트/시연 전용 baseline 재현 경로. 락 없는 read-modify-write 로 동시 주문 시
     * lost-update(오버셀)를 노출한다(쿠폰의 CouponIssueService.issueWithoutLock 대칭).
     *
     * 프로덕션 호출 금지 — OversellingBaselineTest 의 baseline 측정에서만 사용하며 place 와의 Before/After 비교 자료를 만든다.
     */
    @Transactional
    public Order placeWithoutLock(Long memberId, OrderCreateRequest request) {
        return placeInternal(memberId, request, false);
    }

    /**
     * place(락)와 placeWithoutLock(락 없음)의 공유 본문. useLock 만 재고 조회 전략을 가른다 —
     * true 면 AlbumRepository.findByIdForUpdate(행 락 선점), false 면 findById(락 없음).
     */
    private Order placeInternal(Long memberId, OrderCreateRequest request, boolean useLock) {
        validateOwnership(memberId, request.guest());
        // 토큰 유효기간 내 탈퇴(soft delete)한 회원이면 404 로 차단한다 (#187, #171 과 일관) — 게스트는 제외.
        // member_id 는 단순 Long 컬럼이라 FK 500 은 안 나지만, 익명화된 탈퇴회원에 신규 주문이 귀속되는 비정합을 막는다.
        if (memberId != null && !memberRepository.existsByIdAndDeletedAtIsNull(memberId)) {
            throw new MemberNotFoundException();
        }
        // 게스트 + memberCouponId 거부 — 회원 전용 쿠폰을 게스트가 사용하려는 경합을 즉시 차단한다
        // (이슈 #91 DoD: 게스트 + memberCouponId → COUPON_NOT_APPLICABLE 422).
        if (memberId == null && request.memberCouponId() != null) {
            throw new CouponNotApplicableException("게스트 주문에는 쿠폰을 적용할 수 없습니다");
        }

        // 1) 도메인 검증 + 재고 차감 — 실패 시 orderNumber 발급 비용을 아낀다(소진 거절이 다수인 flash-sale 에서 중요).
        // 락 경로의 다중 album 주문만 라인을 albumId 오름차순으로 처리해 락 획득 순서를 일관화한다
        // (서로 다른 순서로 여러 행을 FOR UPDATE 로 잡아 발생하는 deadlock 차단). OrderItem 순서는 기능 무관.
        // 단일 항목(대다수)은 정렬이 무의미하므로 stream 할당을 건너뛴다.
        List<OrderItemRequest> orderedItems = (useLock && request.items().size() > 1)
                ? request.items().stream().sorted(Comparator.comparingLong(OrderItemRequest::albumId)).toList()
                : request.items();
        List<ResolvedLine> lines = new ArrayList<>(orderedItems.size());
        for (OrderItemRequest line : orderedItems) {
            Album album = loadPurchasable(line.albumId(), useLock);
            decreaseStock(album, line.quantity());
            lines.add(new ResolvedLine(album, line.quantity()));
        }

        // 2) orderNumber 발급 + Order/OrderItem 영속화.
        String orderNumber = allocateOrderNumber();
        Order order = newOrder(orderNumber, memberId, request.guest(), toShippingInfo(request.shipping()));
        for (ResolvedLine line : lines) {
            order.addItem(OrderItem.create(line.album, line.quantity));
        }

        Order persisted = orderRepository.save(order);

        // 3) 쿠폰 적용 — 저장 후 orderId 가 확보된 다음 호출한다 (MemberCoupon.use(orderId) 가 orderId 를 기록).
        // 같은 트랜잭션이라 적용 실패는 재고 차감/주문 저장과 함께 롤백된다 (이슈 #91 DoD: 실패 시 정합성 유지).
        // memberId 는 윗 가드(게스트+쿠폰 거부) + validateOwnership XOR 결과로 여기서 memberCouponId 가 비지 않으면 항상 non-null.
        if (request.memberCouponId() != null) {
            long discount = couponApplicationService.applyToOrder(
                    request.memberCouponId(), memberId, persisted);
            log.info("쿠폰 적용: orderId={}, memberCouponId={}, discount={}, payable={}",
                    persisted.getId(), request.memberCouponId(), discount, persisted.getPayableAmount());
        }

        return persisted;
    }

    private record ResolvedLine(Album album, int quantity) {
    }

    private void validateOwnership(Long memberId, GuestInfoRequest guest) {
        boolean hasMember = memberId != null;
        boolean hasGuest = guest != null;
        if (hasMember == hasGuest) {
            throw new InvalidOrderOwnershipException();
        }
    }

    private Order newOrder(String orderNumber, Long memberId, GuestInfoRequest guest, OrderShippingInfo shipping) {
        if (memberId != null) {
            return Order.placeForMember(orderNumber, memberId, shipping);
        }
        return Order.placeForGuest(orderNumber, guest.email(), guest.phone(), shipping);
    }

    private static OrderShippingInfo toShippingInfo(ShippingInfoRequest shipping) {
        return new OrderShippingInfo(
                shipping.recipientName(),
                shipping.recipientPhone(),
                shipping.address(),
                shipping.addressDetail(),
                shipping.zipCode(),
                shipping.safePackagingRequested());
    }

    /**
     * orderNumber 후보를 최대 3회 발급해 DB 미존재 번호를 사전 선점한다 — UNIQUE 충돌 사전 회피.
     * race window 는 남지만 36^6 공간 + 트래픽 규모상 충돌 확률이 무시 가능.
     */
    private String allocateOrderNumber() {
        String candidate = null;
        for (int attempt = 0; attempt < MAX_ORDER_NUMBER_ATTEMPTS; attempt++) {
            candidate = orderNumberGenerator.generate();
            if (!orderRepository.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        return candidate;
    }

    /**
     * 구매 가능 album 로딩 + SELLING 검증. useLock 이면 SELECT ... FOR UPDATE 로 행 락을 선점해(#205)
     * 이어지는 재고 차감의 lost-update 를 막는다. 락 미적용 baseline 경로는 일반 findById.
     */
    private Album loadPurchasable(Long albumId, boolean useLock) {
        Album album = (useLock
                ? albumRepository.findByIdForUpdate(albumId)
                : albumRepository.findById(albumId))
                .orElseThrow(AlbumNotFoundException::new);
        if (album.getStatus() != AlbumStatus.SELLING) {
            throw new AlbumNotPurchasableException();
        }
        return album;
    }

    private void decreaseStock(Album album, int quantity) {
        if (album.getStock() < quantity) {
            throw new InsufficientStockException(album.getId(), quantity, album.getStock());
        }
        album.adjustStock(-quantity);
    }
}
