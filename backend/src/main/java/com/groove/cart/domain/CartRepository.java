package com.groove.cart.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 장바구니 영속성. findByMemberIdWithItems 는 cart 와 cart_item 을 fetch join 한다(album 은 lazy).
 */
public interface CartRepository extends JpaRepository<Cart, Long> {

    Optional<Cart> findByMemberId(Long memberId);

    @Query("SELECT c FROM Cart c LEFT JOIN FETCH c.items WHERE c.memberId = :memberId")
    Optional<Cart> findByMemberIdWithItems(Long memberId);

    /** 앨범 삭제 차단 검사 — 해당 album 을 참조하는 cart_item 존재 여부. */
    @Query("SELECT CASE WHEN COUNT(ci) > 0 THEN true ELSE false END FROM CartItem ci WHERE ci.album.id = :albumId")
    boolean existsByAlbumId(@Param("albumId") Long albumId);
}
