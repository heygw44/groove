package com.groove.review.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.review.dto.ReviewRatingCount;
import com.groove.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, Long> {

	boolean existsByProductIdAndMemberId(Long productId, Long memberId);

	Optional<Review> findByIdAndMemberId(Long id, Long memberId);

	@EntityGraph(attributePaths = "member")
	Page<Review> findByProductId(Long productId, Pageable pageable);

	@Query("""
			SELECT new com.groove.review.dto.ReviewRatingCount(r.rating, COUNT(r))
			FROM Review r
			WHERE r.product.id = :productId
			GROUP BY r.rating
			""")
	List<ReviewRatingCount> countByRatingForProduct(@Param("productId") Long productId);
}
