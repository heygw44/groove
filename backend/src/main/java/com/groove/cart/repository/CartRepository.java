package com.groove.cart.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.cart.entity.Cart;

public interface CartRepository extends JpaRepository<Cart, Long> {

	Optional<Cart> findByMemberId(Long memberId);
}
