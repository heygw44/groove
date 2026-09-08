package com.groove.product.repository;

import java.util.Collection;
import java.util.List;
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

	// images 는 일부러 뺐다. productGenres 와 컬렉션 두 개를 동시에 fetch join 하면 카테시안 곱이 된다.
	@EntityGraph(attributePaths = {"album", "artist", "label", "productGenres", "productGenres.genre"})
	Optional<Product> findDetailById(Long id);

	long countByAlbumIdAndStatusNot(Long albumId, ProductStatus status);

	boolean existsByAlbumId(Long albumId);

	@Query(value = """
			SELECT new com.groove.product.dto.AdminProductSummaryResponse(
				p.id, p.title, a.name, l.name, p.price, p.status,
				(SELECT MIN(i.imageUrl) FROM ProductImage i WHERE i.product = p AND i.sortOrder = 0),
				s.quantity, p.createdAt)
			FROM Product p JOIN p.artist a LEFT JOIN p.label l LEFT JOIN Stock s ON s.product = p
			WHERE (:status IS NULL OR p.status = :status)
			AND (:albumId IS NULL OR p.album.id = :albumId)
			""",
			countQuery = """
			SELECT COUNT(p) FROM Product p
			WHERE (:status IS NULL OR p.status = :status)
			AND (:albumId IS NULL OR p.album.id = :albumId)
			""")
	Page<AdminProductSummaryResponse> findAdminSummaries(@Param("status") ProductStatus status,
			@Param("albumId") Long albumId, Pageable pageable);

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

	// 델타 누적이 아니라 재계산이라 훅이 한 번 빠져도 다음 호출이 실제 집계로 되돌린다.
	// flushAutomatically 는 정확성 조건이다. 주문 상태 변경이 먼저 반영돼야 서브쿼리가 새 상태를 본다.
	// clearAutomatically 는 켜지 않는다. 취소 경로는 이 UPDATE 뒤에도 order 의 lazy 연관을 계속 읽는다.
	@Modifying(flushAutomatically = true)
	@Query(value = """
			UPDATE product p
			SET p.sold_quantity = (
				SELECT COALESCE(SUM(oi.quantity), 0)
				FROM order_item oi
				JOIN orders o ON o.id = oi.order_id
				WHERE oi.product_id = p.id
				AND o.status IN ('PAID', 'PREPARING', 'SHIPPED', 'DELIVERED'))
			WHERE p.id IN (:productIds)
			""", nativeQuery = true)
	void refreshSoldQuantities(@Param("productIds") Collection<Long> productIds);

	@Query("SELECT p.discogsReleaseId FROM Product p WHERE p.discogsReleaseId IN :ids")
	List<Long> findExistingDiscogsReleaseIds(@Param("ids") Collection<Long> ids);

	boolean existsByDiscogsReleaseId(Long discogsReleaseId);
}
