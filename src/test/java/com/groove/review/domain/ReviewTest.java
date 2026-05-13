package com.groove.review.domain;

import com.groove.catalog.album.domain.Album;
import com.groove.catalog.album.domain.AlbumFormat;
import com.groove.catalog.album.domain.AlbumStatus;
import com.groove.catalog.artist.domain.Artist;
import com.groove.catalog.genre.domain.Genre;
import com.groove.member.domain.Member;
import com.groove.order.domain.Order;
import com.groove.order.domain.OrderShippingInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Review 도메인")
class ReviewTest {

    private Member member() {
        return Member.register("u@example.com", "$2a$10$hash", "김민수", "01000000001");
    }

    private Album album() {
        return Album.create("Abbey Road", Artist.create("The Beatles", "d"), Genre.create("Rock"), null,
                (short) 1969, AlbumFormat.LP_12, 35000L, 8, AlbumStatus.SELLING, false, null, null);
    }

    private Order order() {
        return Order.placeForMember("ORD-1", 1L,
                new OrderShippingInfo("김철수", "01012345678", "서울시", "1호", "06234", false));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 5})
    @DisplayName("write → 1~5 평점은 통과")
    void write_acceptsValidRating(int rating) {
        Review review = Review.write(member(), album(), order(), rating, "좋아요");

        assertThat(review.getRating()).isEqualTo(rating);
        assertThat(review.getContent()).isEqualTo("좋아요");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, -1, 100})
    @DisplayName("write → 1~5 범위 밖 평점은 IllegalArgumentException")
    void write_rejectsOutOfRangeRating(int rating) {
        assertThatThrownBy(() -> Review.write(member(), album(), order(), rating, "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rating");
    }

    @Test
    @DisplayName("write → blank/null content 는 null 로 정규화")
    void write_normalizesBlankContent() {
        assertThat(Review.write(member(), album(), order(), 5, "   ").getContent()).isNull();
        assertThat(Review.write(member(), album(), order(), 5, null).getContent()).isNull();
    }

    @Test
    @DisplayName("write → null 인자는 NPE")
    void write_rejectsNulls() {
        assertThatThrownBy(() -> Review.write(null, album(), order(), 5, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Review.write(member(), null, order(), 5, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Review.write(member(), album(), null, 5, "x")).isInstanceOf(NullPointerException.class);
    }
}
