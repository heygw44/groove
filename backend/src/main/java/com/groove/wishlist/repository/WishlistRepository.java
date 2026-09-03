package com.groove.wishlist.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.product.entity.ProductStatus;
import com.groove.wishlist.entity.Wishlist;

public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

	boolean existsByMemberIdAndProductId(Long memberId, Long productId);

	Optional<Wishlist> findByMemberIdAndProductId(Long memberId, Long productId);

	@EntityGraph(attributePaths = {"product", "product.artist"})
	Page<Wishlist> findAllByMemberIdAndProductStatusNot(Long memberId, ProductStatus status, Pageable pageable);
}
