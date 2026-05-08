package com.groove.catalog.album.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 앨범 영속성.
 *
 * <p>FK 역참조 검사용 {@code existsByXxxId} 는 Artist/Genre/Label 의 delete 시
 * IN_USE(409) 사전 검사 (race condition 은 DB ON DELETE RESTRICT 가 최종 방어).
 *
 * <p>공개 검색/조회({@code GET /albums})는 {@link JpaSpecificationExecutor} 로 동적 필터를 조합한다.
 * Specification 정의는 {@link AlbumSpecs} 참조.
 */
public interface AlbumRepository extends JpaRepository<Album, Long>, JpaSpecificationExecutor<Album> {

    boolean existsByArtist_Id(Long artistId);

    boolean existsByGenre_Id(Long genreId);

    boolean existsByLabel_Id(Long labelId);
}
