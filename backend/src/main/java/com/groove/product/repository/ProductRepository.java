package com.groove.product.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.product.dto.AdminProductSummaryResponse;
import com.groove.product.entity.Product;
import com.groove.product.entity.ProductStatus;

public interface ProductRepository extends JpaRepository<Product, Long> {

	@EntityGraph(attributePaths = {"artist", "label", "productGenres", "productGenres.genre"})
	Optional<Product> findDetailById(Long id);

	@Query(value = """
			SELECT new com.groove.product.dto.AdminProductSummaryResponse(
				p.id, p.title, a.name, l.name, p.price, p.status,
				(SELECT MIN(i.imageUrl) FROM ProductImage i WHERE i.product = p AND i.sortOrder = 0),
				s.quantity, p.createdAt)
			FROM Product p JOIN p.artist a LEFT JOIN p.label l LEFT JOIN Stock s ON s.product = p
			WHERE (:status IS NULL OR p.status = :status)
			""",
			countQuery = "SELECT COUNT(p) FROM Product p WHERE (:status IS NULL OR p.status = :status)")
	Page<AdminProductSummaryResponse> findAdminSummaries(@Param("status") ProductStatus status, Pageable pageable);

	// 동시에 여러 리뷰가 생성/삭제돼도 계산식 UPDATE 라 최종적으로는 항상 실제 집계와 같은 값에 수렴한다.
	// flushAutomatically 로 리뷰 INSERT/DELETE 가 이 UPDATE 이전에 DB 에 반영된다.
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(value = """
			UPDATE product p
			SET p.avg_rating = (SELECT ROUND(AVG(r.rating), 1) FROM review r WHERE r.product_id = p.id),
				p.review_count = (SELECT COUNT(*) FROM review r WHERE r.product_id = p.id)
			WHERE p.id = :productId
			""", nativeQuery = true)
	void refreshReviewStats(@Param("productId") Long productId);
}
