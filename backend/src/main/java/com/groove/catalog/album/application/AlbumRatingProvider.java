package com.groove.catalog.album.application;

import java.util.Collection;
import java.util.Map;

/**
 * 앨범 묶음의 리뷰 집계를 가져오는 읽기 전용 포트(#349).
 *
 * 집계는 review 도메인이 소유하므로 catalog 는 이 인터페이스만 의존하고 review 가 구현한다 —
 * catalog→review 역참조를 끊어 슬라이스 단방향(review→catalog)을 유지한다.
 */
public interface AlbumRatingProvider {

    /** albumId → AlbumRating 맵. 리뷰가 없는 앨범은 맵에서 빠진다(호출 측이 기본값으로 보충). */
    Map<Long, AlbumRating> ratingsByAlbumId(Collection<Long> albumIds);
}
