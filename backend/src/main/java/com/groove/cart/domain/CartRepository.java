package com.groove.cart.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 장바구니 영속성.
 *
 * <p>{@link #findByMemberIdWithItems(Long)} 는 cart 와 cart_item 을 fetch join 한다.
 * GET /api/v1/cart 응답에서 album 까지 함께 펼치지만 album 자체는 다음 단계에서 lazy 로
 * 풀린다 — 본 이슈 범위에서는 cart-cart_item 의 N+1 만 막고, album 은 단건 카탈로그
 * 조회와 동일한 동작을 유지한다 (W10 시연 보존 정책과 결).
 */
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByMemberId(Long memberId);

    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.items WHERE c.memberId = :memberId")
    Optional<Cart> findByMemberIdWithItems(Long memberId);

    /**
     * 앨범 삭제 차단 검사 (#159) — 해당 album 을 참조하는 cart_item 존재 여부.
     *
     * <p>{@code cart_item.album_id} 는 ON DELETE RESTRICT FK 다. {@code AlbumService.delete} 가
     * 사전 검사로 호출해 {@code ALBUM_IN_USE}(409) 를 반환하고, 동시 INSERT race 는 DB FK 가 최종 방어한다.
     */
    @Query("SELECT CASE WHEN COUNT(ci) > 0 THEN true ELSE false END FROM CartItem ci WHERE ci.album.id = :albumId")
    boolean existsByAlbumId(@Param("albumId") Long albumId);
}
