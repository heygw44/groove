package com.groove.catalog.album.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /**
     * 공개 검색/목록(#203)용 {@code findAll} 오버라이드. artist/genre/label(모두 {@code @ManyToOne(LAZY)})을
     * ad-hoc {@link EntityGraph} 로 동반 페치해 {@code AlbumSummaryResponse.from()} 의 N+1 SELECT 를 제거한다.
     *
     * <p>세 연관 모두 to-one 이라 컬렉션 페치가 아니므로 페이징 in-memory(HHH000104) 경고 없이
     * 본 쿼리에 OUTER JOIN 으로 인라인된다. Specification 동적 필터·count 쿼리는 그대로 유지된다
     * (Spring Data 는 count 쿼리에 EntityGraph 를 적용하지 않음).
     */
    @Override
    @EntityGraph(attributePaths = {"artist", "genre", "label"})
    Page<Album> findAll(Specification<Album> spec, Pageable pageable);

    boolean existsByArtist_Id(Long artistId);

    boolean existsByGenre_Id(Long genreId);

    boolean existsByLabel_Id(Long labelId);
}
