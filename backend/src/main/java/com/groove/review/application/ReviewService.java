package com.groove.review.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.exception.AlbumNotFoundException;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberNotFoundException;
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

import java.util.Objects;
import java.util.Set;

/**
 * 리뷰 작성/조회/삭제 트랜잭션 경계.
 *
 * 작성(create) 검증 순서:
 * - 주문 존재 — 없으면 OrderNotFoundException (404)
 * - 본인 주문 — order.memberId == 인증 memberId 아니면 ReviewNotOwnedException (403)
 * - 배송 완료 — order.status ∈ {DELIVERED, COMPLETED} 아니면 ReviewOrderNotDeliveredException (422)
 * - 항목 포함 — order.items 중 albumId 가 없으면 AlbumNotInOrderException (422)
 * - 중복 없음 — (orderId, albumId) 리뷰가 이미 있으면 DuplicateReviewException (409). saveAndFlush 의
 *   uk_review_order_album 위반(DataIntegrityViolationException)도 같은 예외로 흡수
 * - 회원 활성 — 탈퇴(soft delete)·삭제된 회원이면 MemberNotFoundException (404)
 *
 * 조회(listByAlbum): 작성자명을 마스킹해 응답. 앨범이 없어도 빈 페이지를 돌려준다.
 *
 * 삭제(delete): 리뷰 미존재 시 ReviewNotFoundException (404), 작성자 ≠ 인증 회원이면 ReviewNotOwnedException (403),
 * 회원 활성 — 탈퇴(soft delete)된 회원이면 MemberNotFoundException (404). 탈퇴 후 만료 전 access 토큰 잔존 방어(#269).
 */
@Service
public class ReviewService {

    /** 리뷰 작성이 허용되는 주문 상태 — DELIVERED 이상. */
    private static final Set<OrderStatus> REVIEWABLE_ORDER_STATUSES = OrderStatus.DELIVERED_OR_COMPLETED;

    /** 1주문-1상품-1리뷰 UNIQUE 제약 이름 — 이 제약 위반만 409 로 흡수한다. */
    private static final String UK_REVIEW_ORDER_ALBUM = "uk_review_order_album";

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

        if (command.memberId() == null || !Objects.equals(order.getMemberId(), command.memberId())) {
            // memberId 가 null 이면 게스트 주문과 매칭돼 통과될 수 있으므로 명시적으로 막는다.
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
        Member member = memberRepository.findByIdAndDeletedAtIsNull(command.memberId())
                .orElseThrow(MemberNotFoundException::new);

        Review review = Review.write(member, album, order, command.rating(), command.content());
        try {
            reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            // uk_review_order_album 위반만 409 로 흡수하고, 그 외 무결성 위반은 전파한다.
            Throwable cause = e.getMostSpecificCause();
            String message = cause != null ? cause.getMessage() : e.getMessage();
            if (message != null && message.contains(UK_REVIEW_ORDER_ALBUM)) {
                throw new DuplicateReviewException();
            }
            throw e;
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
        // 탈퇴(soft delete) 회원이 만료 전 access 토큰으로 접근하는 것을 차단(#269) — create() 와 동일한 활성 검증.
        if (!memberRepository.existsByIdAndDeletedAtIsNull(memberId)) {
            throw new MemberNotFoundException();
        }
        reviewRepository.delete(review);
    }

    private static boolean containsAlbum(Order order, Long albumId) {
        return order.getItems().stream()
                .map(OrderItem::getAlbum)
                .anyMatch(album -> Objects.equals(album.getId(), albumId));
    }
}
