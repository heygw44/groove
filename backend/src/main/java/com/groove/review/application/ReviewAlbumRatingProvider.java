package com.groove.review.application;

import com.groove.catalog.album.application.AlbumRating;
import com.groove.catalog.album.application.AlbumRatingProvider;
import com.groove.review.domain.AlbumRatingView;
import com.groove.review.domain.ReviewRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 앨범 묶음의 리뷰 집계를 1회 쿼리로 가져와 albumId → AlbumRating 으로 변환하는 {@link AlbumRatingProvider}
 * 구현(catalog→review 역참조 차단, #349). 빈 묶음이면 빈 맵.
 */
@Component
public class ReviewAlbumRatingProvider implements AlbumRatingProvider {

    private final ReviewRepository reviewRepository;

    public ReviewAlbumRatingProvider(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    @Override
    public Map<Long, AlbumRating> ratingsByAlbumId(Collection<Long> albumIds) {
        if (albumIds.isEmpty()) {
            return Map.of();
        }
        return reviewRepository.findRatingsByAlbumIds(albumIds).stream()
                .collect(Collectors.toMap(
                        AlbumRatingView::getAlbumId,
                        view -> AlbumRating.of(view.getAverageRating(), view.getReviewCount())));
    }
}
