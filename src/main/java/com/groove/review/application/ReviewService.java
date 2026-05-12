package com.groove.review.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.exception.AlbumNotFoundException;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderItem;
import com.groove.order.domain.OrderRepository;
import com.groove.order.domain.OrderStatus;
import com.groove.order.exception.OrderNotFoundException;
import com.groove.review.api.dto.ReviewResponse;
import com.groove.review.domain.Review;
import com.groove.review.domain.ReviewRepository;
import com.groove.review.exception.AlbumNotInOrderException;
import com.groove.review.exception.DuplicateReviewException;
import com.groove.review.exception.ReviewNotFoundException;
import com.groove.review.exception.ReviewNotOwnedException;
import com.groove.review.exception.ReviewOrderNotDeliveredException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * 리뷰 작성/조회/삭제 트랜잭션 경계 (#59, API.md §3.8).
 *
 * <h2>작성 검증 순서 ({@link #create})</h2>
 * <ol>
 *   <li>주문 존재 — 없으면 {@link OrderNotFoundException} (404)</li>
 *   <li>본인 주문 — {@code order.memberId == 인증 memberId} 아니면 {@link ReviewNotOwnedException} (403). 게스트 주문은 {@code memberId} 가 null 이라 여기서 걸린다</li>
 *   <li>배송 완료 — {@code order.status ∈ {DELIVERED, COMPLETED}} 아니면 {@link ReviewOrderNotDeliveredException} (422)</li>
 *   <li>항목 포함 — {@code order.items} 중 {@code albumId} 가 없으면 {@link AlbumNotInOrderException} (422)</li>
 *   <li>중복 없음 — {@code (orderId, albumId)} 리뷰가 이미 있으면 {@link DuplicateReviewException} (409). 최종 방어선은 {@code uk_review_order_album} — {@code saveAndFlush} 의 {@link DataIntegrityViolationException} 도 같은 예외로 흡수한다</li>
 * </ol>
 *
 * <h2>조회 ({@link #listByAlbum})</h2>
 * 공개 엔드포인트 — 작성자명을 마스킹해 응답한다. 앨범이 존재하지 않아도 빈 페이지를 돌려준다 (공개 조회라 404 로 ID 존재 여부를 흘릴 필요 없음).
 *
 * <h2>삭제 ({@link #delete})</h2>
 * 리뷰 미존재 시 {@link ReviewNotFoundException} (404), 작성자 ≠ 인증 회원이면 {@link ReviewNotOwnedException} (403).
 */
@Service
public class ReviewService {

    /** 리뷰 작성이 허용되는 주문 상태 — "DELIVERED 이상" (glossary §2.10, API.md §3.8). */
    private static final Set<OrderStatus> REVIEWABLE_ORDER_STATUSES =
            EnumSet.of(OrderStatus.DELIVERED, OrderStatus.COMPLETED);

    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final AlbumRepository albumRepository;
    private final MemberRepository memberRepository;

    public ReviewService(ReviewRepository reviewRepository,
                         OrderRepository orderRepository,
                         AlbumRepository albumRepository,
                         MemberRepository memberRepository) {
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.albumRepository = albumRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public ReviewResponse create(ReviewCreateCommand command) {
        Order order = orderRepository.findByOrderNumber(command.orderNumber())
                .orElseThrow(OrderNotFoundException::new);

        if (!Objects.equals(order.getMemberId(), command.memberId())) {
            throw new ReviewNotOwnedException();
        }
        if (!REVIEWABLE_ORDER_STATUSES.contains(order.getStatus())) {
            throw new ReviewOrderNotDeliveredException();
        }
        if (!containsAlbum(order, command.albumId())) {
            throw new AlbumNotInOrderException();
        }
        if (reviewRepository.existsByOrderIdAndAlbumId(order.getId(), command.albumId())) {
            throw new DuplicateReviewException();
        }

        Album album = albumRepository.findById(command.albumId())
                .orElseThrow(AlbumNotFoundException::new);
        Member member = memberRepository.getReferenceById(command.memberId());

        Review review = Review.write(member, album, order, command.rating(), command.content());
        try {
            reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            // 동시 작성 경합 — uk_review_order_album 위반. 선검증을 빠져나간 케이스를 최종 방어선에서 흡수한다.
            throw new DuplicateReviewException();
        }
        return ReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> listByAlbum(Long albumId, Pageable pageable) {
        return reviewRepository.findByAlbumId(albumId, pageable).map(ReviewResponse::from);
    }

    @Transactional
    public void delete(Long memberId, Long reviewId) {
        Review review = reviewRepository.findWithMemberById(reviewId)
                .orElseThrow(ReviewNotFoundException::new);
        if (!Objects.equals(review.getMember().getId(), memberId)) {
            throw new ReviewNotOwnedException();
        }
        reviewRepository.delete(review);
    }

    private static boolean containsAlbum(Order order, Long albumId) {
        return order.getItems().stream()
                .map(OrderItem::getAlbum)
                .anyMatch(album -> Objects.equals(album.getId(), albumId));
    }
}
