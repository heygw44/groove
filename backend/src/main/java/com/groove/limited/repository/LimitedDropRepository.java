package com.groove.limited.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.limited.dto.AdminLimitedDropSummaryResponse;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;

public interface LimitedDropRepository extends JpaRepository<LimitedDrop, Long> {

	Optional<LimitedDrop> findByProductId(Long productId);

	boolean existsByProductIdAndStatusNot(Long productId, LimitedDropStatus status);

	@EntityGraph(attributePaths = "product")
	Optional<LimitedDrop> findWithProductById(Long id);

	@Query(value = """
			SELECT new com.groove.limited.dto.AdminLimitedDropSummaryResponse(
				d.id, d.product.id, d.product.title, d.totalQuantity, d.soldCount, d.perMemberLimit,
				d.openAt, d.closeAt, d.status, d.createdAt)
			FROM LimitedDrop d
			WHERE (:status IS NULL OR d.status = :status)
			""",
			countQuery = "SELECT COUNT(d) FROM LimitedDrop d WHERE (:status IS NULL OR d.status = :status)")
	Page<AdminLimitedDropSummaryResponse> findAdminSummaries(@Param("status") LimitedDropStatus status,
			Pageable pageable);
}
