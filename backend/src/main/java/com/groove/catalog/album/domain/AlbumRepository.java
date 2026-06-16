package com.groove.catalog.album.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 앨범 영속성. existsByXxxId 는 Artist/Genre/Label delete 시 IN_USE(409) 사전 검사,
 * 공개 검색은 JpaSpecificationExecutor 로 동적 필터를 조합한다.
 */
public interface AlbumRepository extends JpaRepository<Album, Long>, JpaSpecificationExecutor<Album> {

    /**
     * 공개 검색/목록용 findAll 오버라이드. artist/genre/label 을 EntityGraph 로 동반 페치한다.
     */
    @Override
    @EntityGraph(attributePaths = {"artist", "genre", "label"})
    Page<Album> findAll(Specification<Album> spec, Pageable pageable);

    /**
     * 주문 재고 차감용 비관적 락 조회 — album 행을 SELECT ... FOR UPDATE 로 잠근다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Album a WHERE a.id = :id")
    Optional<Album> findByIdForUpdate(@Param("id") Long id);

    boolean existsByArtist_Id(Long artistId);

    boolean existsByGenre_Id(Long genreId);

    boolean existsByLabel_Id(Long labelId);

    /**
     * 비정규화 artist_name 동기화 — artist 이름 변경 시 해당 artist 의 모든 album 을 일괄 갱신한다.
     */
    @Modifying
    @Query("UPDATE Album a SET a.artistName = :name WHERE a.artist.id = :artistId")
    int updateArtistNameByArtistId(@Param("artistId") Long artistId, @Param("name") String name);

    /**
     * 재고 복원용 원자적 가산 UPDATE — 한 문장 안에서 stock = stock + :delta 를 적용한다. delta 는 양수만 전달된다.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Album a SET a.stock = a.stock + :delta WHERE a.id = :id")
    int restoreStock(@Param("id") Long id, @Param("delta") int delta);
}
