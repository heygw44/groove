package com.groove.order.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.album.exception.AlbumNotFoundException;
import com.groove.cart.exception.AlbumNotPurchasableException;
import com.groove.order.api.dto.GuestInfoRequest;
import com.groove.order.api.dto.OrderCreateRequest;
import com.groove.order.api.dto.OrderItemRequest;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.exception.InsufficientStockException;
import com.groove.order.exception.InvalidOrderOwnershipException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 생성 트랜잭션 경계 (issue #43, API.md §3.5).
 *
 * <p>처리 순서:
 * <ol>
 *   <li>회원/게스트 정합성 가드 (XOR).</li>
 *   <li>각 라인 album 로딩 + 구매 가능(SELLING) 검증.</li>
 *   <li>재고 검증 + 차감 — <b>락 없이 단순 구현</b>. 동일 album 동시 주문 시
 *       lost-update / oversell 가능성을 의도적으로 노출 (W6-6 재현, W10-3 비관적 락 시연).</li>
 *   <li>orderNumber 발급 (사전 존재 체크 최대 3회).</li>
 *   <li>Order + OrderItem 생성 후 영속화.</li>
 *   <li>응답 직렬화 시 LazyInitializationException 방지를 위해 album/artist 강제 초기화.</li>
 * </ol>
 *
 * <p>RuntimeException 이 발생하면 Spring 의 기본 정책으로 트랜잭션 전체가 롤백되어 재고 차감도 함께 되돌려진다.
 */
@Service
public class OrderService {

    private static final int MAX_ORDER_NUMBER_ATTEMPTS = 3;

    private final OrderRepository orderRepository;
    private final AlbumRepository albumRepository;
    private final OrderNumberGenerator orderNumberGenerator;

    public OrderService(OrderRepository orderRepository,
                        AlbumRepository albumRepository,
                        OrderNumberGenerator orderNumberGenerator) {
        this.orderRepository = orderRepository;
        this.albumRepository = albumRepository;
        this.orderNumberGenerator = orderNumberGenerator;
    }

    @Transactional
    public Order place(Long memberId, OrderCreateRequest request) {
        validateOwnership(memberId, request.guest());

        // 1) 도메인 검증 + 재고 차감 — 실패 시 orderNumber 발급 비용을 아낀다.
        Album[] albums = new Album[request.items().size()];
        for (int i = 0; i < request.items().size(); i++) {
            OrderItemRequest line = request.items().get(i);
            Album album = loadPurchasable(line.albumId());
            decreaseStock(album, line.quantity());
            albums[i] = album;
        }

        // 2) orderNumber 발급 + Order/OrderItem 영속화.
        String orderNumber = allocateOrderNumber();
        Order order = newOrder(orderNumber, memberId, request.guest());
        for (int i = 0; i < albums.length; i++) {
            order.addItem(OrderItem.create(albums[i], request.items().get(i).quantity()));
        }

        Order saved = orderRepository.save(order);
        initializeAssociations(saved);
        return saved;
    }

    private void validateOwnership(Long memberId, GuestInfoRequest guest) {
        boolean hasMember = memberId != null;
        boolean hasGuest = guest != null;
        if (hasMember == hasGuest) {
            throw new InvalidOrderOwnershipException();
        }
    }

    private Order newOrder(String orderNumber, Long memberId, GuestInfoRequest guest) {
        if (memberId != null) {
            return Order.placeForMember(orderNumber, memberId);
        }
        return Order.placeForGuest(orderNumber, guest.email(), guest.phone());
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

    private Album loadPurchasable(Long albumId) {
        Album album = albumRepository.findById(albumId)
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

    /**
     * OrderResponse 직렬화 시점에 album.title / album.artist.name 이 필요하므로
     * 트랜잭션 내에서 LAZY 프록시를 강제 초기화한다. cart 와 동일한 패턴.
     */
    private void initializeAssociations(Order order) {
        order.getItems().forEach(item -> {
            Album album = item.getAlbum();
            album.getTitle();
            album.getArtist().getName();
        });
    }
}
