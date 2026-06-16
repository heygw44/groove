package com.groove.review.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /** 1주문-1상품-1리뷰 선검증 — 중복 작성 시도를 미리 거른다. */
    boolean existsByOrderIdAndAlbumId(Long orderId, Long albumId);

    /**
     * 상품별 리뷰 목록 (GET /albums/{id}/reviews) — 작성자명 마스킹을 위해 member 까지 fetch 한다.
     * 정렬은 컨트롤러가 createdAt 화이트리스트로 강제한다.
     */
    @EntityGraph(attributePaths = "member")
    Page<Review> findByAlbumId(Long albumId, Pageable pageable);

    /** 단건 + member fetch — 삭제 시 소유자 검증에서 LAZY 프록시 추가 조회를 막는다. */
    @EntityGraph(attributePaths = "member")
    Optional<Review> findWithMemberById(Long id);

    /**
     * 앨범 묶음의 리뷰 집계 — 페이지 단위로 1회 호출한다. 리뷰가 없는 앨범은 결과에 포함되지 않으므로(GROUP BY)
     * 호출 측에서 기본값(null/0)으로 보충한다.
     */
    @Query("""
            select r.album.id as albumId, avg(r.rating) as averageRating, count(r) as reviewCount
            from Review r
            where r.album.id in :albumIds
            group by r.album.id
            """)
    List<AlbumRatingView> findRatingsByAlbumIds(@Param("albumIds") Collection<Long> albumIds);
}
