package com.groove.catalog.album.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 앨범 영속성.
 *
 * <p>FK 역참조 검사용 {@code existsByXxxId} 는 Artist/Genre/Label 의 delete 시
 * IN_USE(409) 사전 검사 (race condition 은 DB ON DELETE RESTRICT 가 최종 방어).
 * 검색/조회 쿼리는 W6 (별도 이슈) 범위.
 */
public interface AlbumRepository extends JpaRepository<Album, Long> {

    boolean existsByArtist_Id(Long artistId);

    boolean existsByGenre_Id(Long genreId);

    boolean existsByLabel_Id(Long labelId);
}
