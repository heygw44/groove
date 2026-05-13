package com.groove.review.application;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumRepository;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.album.exception.AlbumNotFoundException;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewService 단위 테스트")
class ReviewServiceTest {

    private static final long OWNER_ID = 1L;
    private static final long OTHER_ID = 2L;
    private static final long ALBUM_ID = 10L;
    private static final long ORDER_ID = 100L;
    private static final String ORDER_NUMBER = "ORD-20260512-A00001";

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private AlbumRepository albumRepository;
    @Mock
    private MemberRepository memberRepository;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, orderRepository, albumRepository, memberRepository);
    }

    private Album albumWithId(long id) {
        Album album = Album.create("Abbey Road", Artist.create("The Beatles", "d"), Genre.create("Rock"), null,
                (short) 1969, AlbumFormat.LP_12, 35000L, 8, AlbumStatus.SELLING, false, null, null);
        ReflectionTestUtils.setField(album, "id", id);
        return album;
    }

    private Member memberWithId(long id, String name) {
        Member member = Member.register("u" + id + "@example.com", "$2a$10$hash", name, "0100000000" + id);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private Order memberOrder(Long memberId, OrderStatus status, Album item) {
        Order order = Order.placeForMember(ORDER_NUMBER, memberId,
                new com.groove.order.domain.OrderShippingInfo("김철수", "01012345678", "서울시 강남구", "1호", "06234", false));
        if (item != null) {
            order.addItem(OrderItem.create(item, 1));
        }
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        // PENDING → ... → status 까지 합법 전이로 끌어올린다.
        for (OrderStatus next : pathTo(status)) {
            order.changeStatus(next, null);
        }
        return order;
    }

    private static java.util.List<OrderStatus> pathTo(OrderStatus target) {
        return switch (target) {
            case PENDING -> java.util.List.of();
            case PAID -> java.util.List.of(OrderStatus.PAID);
            case PREPARING -> java.util.List.of(OrderStatus.PAID, OrderStatus.PREPARING);
            case SHIPPED -> java.util.List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED);
            case DELIVERED -> java.util.List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);
            case COMPLETED -> java.util.List.of(OrderStatus.PAID, OrderStatus.PREPARING, OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.COMPLETED);
            default -> throw new IllegalArgumentException(target.name());
        };
    }

    private ReviewCreateCommand command() {
        return new ReviewCreateCommand(OWNER_ID, ORDER_NUMBER, ALBUM_ID, 5, "음질 정말 좋네요");
    }

    @Test
    @DisplayName("create → 본인·DELIVERED·항목 포함·중복 없음 → 리뷰 저장 + 마스킹 응답")
    void create_happyPath() {
        Album album = albumWithId(ALBUM_ID);
        Order order = memberOrder(OWNER_ID, OrderStatus.DELIVERED, album);
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(reviewRepository.existsByOrderIdAndAlbumId(ORDER_ID, ALBUM_ID)).willReturn(false);
        given(albumRepository.findById(ALBUM_ID)).willReturn(Optional.of(album));
        given(memberRepository.getReferenceById(OWNER_ID)).willReturn(memberWithId(OWNER_ID, "김민수"));
        given(reviewRepository.saveAndFlush(any(Review.class))).willAnswer(inv -> {
            Review r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 7L);
            return r;
        });

        ReviewResponse response = reviewService.create(command());

        assertThat(response.reviewId()).isEqualTo(7L);
        assertThat(response.memberName()).isEqualTo("김**");
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.content()).isEqualTo("음질 정말 좋네요");
        then(reviewRepository).should().saveAndFlush(any(Review.class));
    }

    @Test
    @DisplayName("create → COMPLETED 주문도 허용")
    void create_allowsCompletedOrder() {
        Album album = albumWithId(ALBUM_ID);
        Order order = memberOrder(OWNER_ID, OrderStatus.COMPLETED, album);
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(reviewRepository.existsByOrderIdAndAlbumId(ORDER_ID, ALBUM_ID)).willReturn(false);
        given(albumRepository.findById(ALBUM_ID)).willReturn(Optional.of(album));
        given(memberRepository.getReferenceById(OWNER_ID)).willReturn(memberWithId(OWNER_ID, "Lee"));
        given(reviewRepository.saveAndFlush(any(Review.class))).willAnswer(inv -> inv.getArgument(0));

        ReviewResponse response = reviewService.create(command());

        assertThat(response.memberName()).isEqualTo("L**");
    }

    @Test
    @DisplayName("create → 주문 미존재 → 404 OrderNotFound")
    void create_orderMissing() {
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.create(command()))
                .isInstanceOf(OrderNotFoundException.class);
        then(reviewRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("create → 타인 주문 → 403 ReviewNotOwned")
    void create_notOwnersOrder() {
        Order order = memberOrder(OTHER_ID, OrderStatus.DELIVERED, albumWithId(ALBUM_ID));
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> reviewService.create(command()))
                .isInstanceOf(ReviewNotOwnedException.class);
    }

    @Test
    @DisplayName("create → 게스트 주문(memberId=null) → 403 ReviewNotOwned")
    void create_guestOrder() {
        Order order = Order.placeForGuest(ORDER_NUMBER, "guest@example.com", "01099998888",
                new com.groove.order.domain.OrderShippingInfo("김철수", "01012345678", "서울시", "1호", "06234", false));
        order.addItem(OrderItem.create(albumWithId(ALBUM_ID), 1));
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        for (OrderStatus next : pathTo(OrderStatus.DELIVERED)) {
            order.changeStatus(next, null);
        }
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> reviewService.create(command()))
                .isInstanceOf(ReviewNotOwnedException.class);
    }

    @Test
    @DisplayName("create → 배송 미완료(SHIPPED) 주문 → 422 ReviewOrderNotDelivered")
    void create_orderNotDelivered() {
        Order order = memberOrder(OWNER_ID, OrderStatus.SHIPPED, albumWithId(ALBUM_ID));
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> reviewService.create(command()))
                .isInstanceOf(ReviewOrderNotDeliveredException.class);
    }

    @Test
    @DisplayName("create → 주문에 해당 album 없음 → 422 AlbumNotInOrder")
    void create_albumNotInOrder() {
        Order order = memberOrder(OWNER_ID, OrderStatus.DELIVERED, albumWithId(999L));
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> reviewService.create(command()))
                .isInstanceOf(AlbumNotInOrderException.class);
    }

    @Test
    @DisplayName("create → 이미 작성한 (주문,앨범) → 409 DuplicateReview (선검증)")
    void create_duplicateByPrecheck() {
        Order order = memberOrder(OWNER_ID, OrderStatus.DELIVERED, albumWithId(ALBUM_ID));
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(reviewRepository.existsByOrderIdAndAlbumId(ORDER_ID, ALBUM_ID)).willReturn(true);

        assertThatThrownBy(() -> reviewService.create(command()))
                .isInstanceOf(DuplicateReviewException.class);
        then(reviewRepository).should(never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("create → 동시 작성 경합으로 UNIQUE 위반 → 409 DuplicateReview (최종 방어선)")
    void create_duplicateByConstraint() {
        Album album = albumWithId(ALBUM_ID);
        Order order = memberOrder(OWNER_ID, OrderStatus.DELIVERED, album);
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(reviewRepository.existsByOrderIdAndAlbumId(ORDER_ID, ALBUM_ID)).willReturn(false);
        given(albumRepository.findById(ALBUM_ID)).willReturn(Optional.of(album));
        given(memberRepository.getReferenceById(OWNER_ID)).willReturn(memberWithId(OWNER_ID, "박"));
        given(reviewRepository.saveAndFlush(any(Review.class)))
                .willThrow(new DataIntegrityViolationException("uk_review_order_album"));

        assertThatThrownBy(() -> reviewService.create(command()))
                .isInstanceOf(DuplicateReviewException.class);
    }

    @Test
    @DisplayName("create → uk_review_order_album 외 무결성 위반 → 그대로 전파")
    void create_otherIntegrityViolationPropagates() {
        Album album = albumWithId(ALBUM_ID);
        Order order = memberOrder(OWNER_ID, OrderStatus.DELIVERED, album);
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(reviewRepository.existsByOrderIdAndAlbumId(ORDER_ID, ALBUM_ID)).willReturn(false);
        given(albumRepository.findById(ALBUM_ID)).willReturn(Optional.of(album));
        given(memberRepository.getReferenceById(OWNER_ID)).willReturn(memberWithId(OWNER_ID, "박"));
        given(reviewRepository.saveAndFlush(any(Review.class)))
                .willThrow(new DataIntegrityViolationException("fk_review_member 위반"));

        assertThatThrownBy(() -> reviewService.create(command()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("create → album 행이 사라진 경우 → 404 AlbumNotFound")
    void create_albumRowMissing() {
        Order order = memberOrder(OWNER_ID, OrderStatus.DELIVERED, albumWithId(ALBUM_ID));
        given(orderRepository.findByOrderNumber(ORDER_NUMBER)).willReturn(Optional.of(order));
        given(reviewRepository.existsByOrderIdAndAlbumId(ORDER_ID, ALBUM_ID)).willReturn(false);
        given(albumRepository.findById(ALBUM_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.create(command()))
                .isInstanceOf(AlbumNotFoundException.class);
    }

    @Test
    @DisplayName("delete → 본인 리뷰 → 삭제")
    void delete_ownReview() {
        Review review = Review.write(memberWithId(OWNER_ID, "김민수"), albumWithId(ALBUM_ID),
                memberOrder(OWNER_ID, OrderStatus.DELIVERED, albumWithId(ALBUM_ID)), 4, "ok");
        ReflectionTestUtils.setField(review, "id", 7L);
        given(reviewRepository.findWithMemberById(7L)).willReturn(Optional.of(review));

        reviewService.delete(OWNER_ID, 7L);

        then(reviewRepository).should().delete(review);
    }

    @Test
    @DisplayName("delete → 미존재 리뷰 → 404 ReviewNotFound")
    void delete_missing() {
        given(reviewRepository.findWithMemberById(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.delete(OWNER_ID, 7L))
                .isInstanceOf(ReviewNotFoundException.class);
        then(reviewRepository).should(never()).delete(any(Review.class));
    }

    @Test
    @DisplayName("delete → 타인 리뷰 → 403 ReviewNotOwned")
    void delete_notOwner() {
        Review review = Review.write(memberWithId(OTHER_ID, "이영희"), albumWithId(ALBUM_ID),
                memberOrder(OTHER_ID, OrderStatus.DELIVERED, albumWithId(ALBUM_ID)), 4, "ok");
        ReflectionTestUtils.setField(review, "id", 7L);
        given(reviewRepository.findWithMemberById(7L)).willReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.delete(OWNER_ID, 7L))
                .isInstanceOf(ReviewNotOwnedException.class);
        then(reviewRepository).should(never()).delete(any(Review.class));
    }
}
