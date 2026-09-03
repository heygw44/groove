package com.groove.review.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.groove.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

	boolean existsByProductIdAndMemberId(Long productId, Long memberId);

	Optional<Review> findByIdAndMemberId(Long id, Long memberId);

	@EntityGraph(attributePaths = "member")
	Page<Review> findByProductId(Long productId, Pageable pageable);
}
