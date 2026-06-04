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

    /**
     * 1주문-1상품-1리뷰 선검증 — 최종 방어선은 {@code uk_review_order_album} UNIQUE 이지만, 흔한 중복 작성 시도는
     * 이 조회로 미리 거른다 (배송 도메인의 {@code existsByOrderId} 와 같은 패턴).
     */
    boolean existsByOrderIdAndAlbumId(Long orderId, Long albumId);

    /**
     * 상품별 리뷰 목록 ({@code GET /albums/{id}/reviews}) — 작성자명 마스킹을 위해 {@code member} 까지 fetch 한다
     * ({@code album}/{@code order} 는 응답 DTO 가 참조하지 않으므로 join 하지 않는다). 작성자명만 쓰므로
     * member 행 전체 join 은 다소 과하지만, 대안인 N+1(페이지당 size 회)을 피하는 쪽이 명백히 낫다.
     *
     * <p>정렬은 컨트롤러가 {@code createdAt} 화이트리스트로 강제한다 — ERD §5.2 의 {@code idx_review_album_created}
     * 는 W10 추가 예정이라, 그 전까지는 {@code fk_review_album} 인덱스 프리픽스 스캔 + 정렬 filesort 다 (의도된 슬로우 쿼리).
     */
    @EntityGraph(attributePaths = "member")
    Page<Review> findByAlbumId(Long albumId, Pageable pageable);

    /**
     * 단건 + member fetch — 삭제 시 소유자 검증({@code review.member.id == 인증 memberId})에서 LAZY 프록시 추가 조회를 막는다.
     */
    @EntityGraph(attributePaths = "member")
    Optional<Review> findWithMemberById(Long id);

    /**
     * 앨범 묶음의 리뷰 집계 — 카탈로그 목록 응답을 만들 때 페이지 단위로 1회 호출해 N+1 을 피한다.
     * 리뷰가 한 건도 없는 앨범은 결과에 포함되지 않으므로(GROUP BY), 호출 측에서 기본값(null/0)으로 보충한다.
     * {@code albumIds} 가 비어 있으면 빈 리스트를 돌려주도록 호출 측에서 먼저 거른다 (빈 IN 절 회피).
     */
    @Query("""
            select r.album.id as albumId, avg(r.rating) as averageRating, count(r) as reviewCount
            from Review r
            where r.album.id in :albumIds
            group by r.album.id
            """)
    List<AlbumRatingView> findRatingsByAlbumIds(@Param("albumIds") Collection<Long> albumIds);
}
