package com.groove.order.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumRepository;
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
import com.groove.order.api.dto.OrderResponse;
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

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 주문 생성 트랜잭션 경계.
 * 재고 차감은 비관적 락으로 직렬화하고, 다중 album 은 데드락 회피를 위해 albumId 오름차순으로 락을 잡는다.
 * 복원 경로는 원자적 가산 UPDATE.
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
    private final Clock clock;

    public OrderService(OrderRepository orderRepository,
                        AlbumRepository albumRepository,
                        OrderNumberGenerator orderNumberGenerator,
                        CouponApplicationService couponApplicationService,
                        MemberRepository memberRepository,
                        Clock clock) {
        this.orderRepository = orderRepository;
        this.albumRepository = albumRepository;
        this.orderNumberGenerator = orderNumberGenerator;
        this.couponApplicationService = couponApplicationService;
        this.memberRepository = memberRepository;
        this.clock = clock;
    }

    /** 회원 본인 주문 단건 조회. 타 회원/게스트 주문은 404 통일. */
    @Transactional(readOnly = true)
    public Order findForMember(Long memberId, String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(OrderNotFoundException::new);
        if (order.isGuestOrder() || !order.getMemberId().equals(memberId)) {
            throw new OrderNotFoundException();
        }
        return order;
    }

    /** 게스트 주문 단건 조회. email 불일치·회원 주문 접근·guestEmail NULL 은 모두 404 통일. */
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

    @Transactional(readOnly = true)
    public Page<Order> listForMember(Long memberId, OrderStatus status, Pageable pageable) {
        Page<Order> page = (status == null)
                ? orderRepository.findByMemberId(memberId, pageable)
                : orderRepository.findByMemberIdAndStatus(memberId, status, pageable);
        // 트랜잭션 안에서 items lazy 초기화 (응답 직렬화 시 lazy 예외 회피).
        page.forEach(order -> order.getItems().size());
        return page;
    }

    /** 회원 주문 목록 keyset(커서) 페이징. Scroll API 로 Window 반환. */
    @Transactional(readOnly = true)
    public Window<Order> listForMemberKeyset(Long memberId, OrderStatus status, int size, Sort sort, ScrollPosition position) {
        Specification<Order> spec = OrderSpecifications.hasMemberId(memberId);
        if (status != null) {
            spec = spec.and(OrderSpecifications.hasStatus(status));
        }
        Window<Order> window = orderRepository.findBy(spec, query -> query.sortBy(sort).limit(size).scroll(position));
        window.forEach(order -> order.getItems().size()); // 트랜잭션 안 lazy 초기화
        return window;
    }

    /** 회원 본인 주문 취소. PENDING 한정. */
    @Transactional
    public Order cancel(Long memberId, String orderNumber, String reason) {
        // PESSIMISTIC_WRITE 로 잠그고 상태 재검증 — 동시 취소(더블클릭/재시도)의 재고·쿠폰 이중 복원 차단.
        Order order = orderRepository.findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(OrderNotFoundException::new);
        if (order.isGuestOrder() || !order.getMemberId().equals(memberId)) {
            throw new OrderNotFoundException();
        }
        if (!memberRepository.existsByIdAndDeletedAtIsNull(memberId)) {
            throw new MemberNotFoundException();
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new IllegalStateTransitionException(order.getStatus(), OrderStatus.CANCELLED);
        }
        order.changeStatus(OrderStatus.CANCELLED, reason, clock.instant());
        // 재고 복원. 락 조회는 items 를 fetch 안 하므로 이 순회가 응답 직렬화 전 lazy 초기화도 겸한다.
        StockRestorer.restore(albumRepository, order.getItems().stream()
                .collect(Collectors.groupingBy(item -> item.getAlbum().getId(), Collectors.summingInt(OrderItem::getQuantity))));
        couponApplicationService.restoreForOrder(order.getId()); // 미적용 주문이면 no-op
        return order;
    }

    /** 엔티티 반환 주문 생성 — 시더·테스트용 내부 호출. HTTP 경로는 멱등 래핑되는 placeAndRespond 를 쓴다. */
    @Transactional
    public Order place(Long memberId, OrderCreateRequest request) {
        return placeInternal(memberId, request, true);
    }

    /**
     * 주문 생성 + DTO 변환 — IdempotencyService.execute 래핑용. execute 의 action 은 JSON 왕복 가능한 DTO 를
     * 반환해야 하고, OrderResponse.from 이 lazy 필드를 즉시 초기화하므로 트랜잭션 안에서 변환한다.
     */
    @Transactional
    public OrderResponse placeAndRespond(Long memberId, OrderCreateRequest request) {
        return OrderResponse.from(placeInternal(memberId, request, true));
    }

    /** 락 없는 baseline 경로 — 동시성 시연/테스트 전용. 프로덕션 호출 금지. */
    @Transactional
    public Order placeWithoutLock(Long memberId, OrderCreateRequest request) {
        return placeInternal(memberId, request, false);
    }

    /** place/placeWithoutLock 공유 본문. useLock 이 재고 조회 전략(행 락 선점 vs findAllById)을 가른다. */
    private Order placeInternal(Long memberId, OrderCreateRequest request, boolean useLock) {
        validateOwnership(memberId, request.guest());
        if (memberId != null && !memberRepository.existsByIdAndDeletedAtIsNull(memberId)) {
            throw new MemberNotFoundException();
        }
        if (memberId == null && request.memberCouponId() != null) {
            throw new CouponNotApplicableException("게스트 주문에는 쿠폰을 적용할 수 없습니다");
        }

        // orderNumber 사전발급은 비락 조회라 행 락 보유 구간 밖에서 끝낸다.
        String orderNumber = allocateOrderNumber();

        List<ResolvedLine> lines = resolveLines(request.items(), useLock);

        Order order = newOrder(orderNumber, memberId, request.guest(), toShippingInfo(request.shipping()));
        for (ResolvedLine line : lines) {
            order.addItem(OrderItem.create(line.album, line.quantity));
        }

        Order persisted = orderRepository.save(order);

        // 쿠폰 적용은 orderId 확보 후. 할인액 산정은 coupon 에 위임하되 반영(applyDiscount)은 order 가 한다(슬라이스 단방향).
        if (request.memberCouponId() != null) {
            long discount = couponApplicationService.applyToOrder(
                    request.memberCouponId(), memberId, persisted.getId(), persisted.getTotalAmount());
            persisted.applyDiscount(discount);
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

    /** 충돌 시 최대 3회 재발급해 미존재 orderNumber 를 선점. */
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

    /** 항목별 album 검증 + 재고 차감 후 ResolvedLine 목록 생성. */
    private List<ResolvedLine> resolveLines(List<OrderItemRequest> items, boolean useLock) {
        // 락 경로는 데드락 회피를 위해 albumId 오름차순 단건 행 락, 비락은 findAllById 일괄. 이후 차감 루프는 공유.
        List<OrderItemRequest> ordered;
        Function<Long, Album> resolve;
        if (useLock) {
            ordered = items.size() > 1
                    ? items.stream().sorted(Comparator.comparingLong(OrderItemRequest::albumId)).toList()
                    : items;
            resolve = albumId -> validatePurchasable(albumRepository.findByIdForUpdate(albumId).orElse(null));
        } else {
            ordered = items;
            Map<Long, Album> byId = albumRepository.findAllById(
                            items.stream().map(OrderItemRequest::albumId).distinct().toList()).stream()
                    .collect(Collectors.toMap(Album::getId, album -> album));
            resolve = albumId -> validatePurchasable(byId.get(albumId));
        }

        List<ResolvedLine> lines = new ArrayList<>(ordered.size());
        for (OrderItemRequest line : ordered) {
            Album album = resolve.apply(line.albumId());
            decreaseStock(album, line.quantity());
            lines.add(new ResolvedLine(album, line.quantity()));
        }
        return lines;
    }

    private static Album validatePurchasable(Album album) {
        if (album == null) {
            throw new AlbumNotFoundException();
        }
        if (!album.isSelling()) {
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
