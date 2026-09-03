package com.groove.cart.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.cart.entity.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

	@EntityGraph(attributePaths = {"product", "product.artist"})
	List<CartItem> findAllByCartIdOrderByIdAsc(Long cartId);

	Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

	@EntityGraph(attributePaths = {"product", "product.artist"})
	Optional<CartItem> findByIdAndCartMemberId(Long id, Long memberId);

	@Modifying(clearAutomatically = true)
	@Query("delete from CartItem ci where ci.cart.id = :cartId")
	void deleteAllByCartId(@Param("cartId") Long cartId);
}
