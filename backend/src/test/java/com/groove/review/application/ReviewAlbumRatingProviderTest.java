package com.groove.review.application;

import com.groove.catalog.album.application.AlbumRating;
import com.groove.review.domain.AlbumRatingView;
import com.groove.review.domain.ReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewAlbumRatingProvider 단위 테스트")
class ReviewAlbumRatingProviderTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Test
    @DisplayName("빈 albumIds → 리뷰 집계 쿼리를 호출하지 않고 빈 맵 반환 (빈 IN 절 회피)")
    void emptyIds_doesNotQuery() {
        ReviewAlbumRatingProvider provider = new ReviewAlbumRatingProvider(reviewRepository);

        Map<Long, AlbumRating> result = provider.ratingsByAlbumId(List.of());

        assertThat(result).isEmpty();
        then(reviewRepository).should(never()).findRatingsByAlbumIds(anyCollection());
    }

    @Test
    @DisplayName("집계 결과를 albumId→AlbumRating 으로 변환하고 평균을 소수 1자리로 반올림한다")
    void mapsAndRoundsAverage() {
        AlbumRatingView view = org.mockito.Mockito.mock(AlbumRatingView.class);
        given(view.getAlbumId()).willReturn(1L);
        given(view.getAverageRating()).willReturn(4.75);
        given(view.getReviewCount()).willReturn(4L);
        given(reviewRepository.findRatingsByAlbumIds(List.of(1L))).willReturn(List.of(view));
        ReviewAlbumRatingProvider provider = new ReviewAlbumRatingProvider(reviewRepository);

        Map<Long, AlbumRating> result = provider.ratingsByAlbumId(List.of(1L));

        assertThat(result).containsKey(1L);
        assertThat(result.get(1L).averageRating()).isEqualTo(4.8);
        assertThat(result.get(1L).reviewCount()).isEqualTo(4L);
    }
}
