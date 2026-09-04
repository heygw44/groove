package com.groove.limited.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.limited.dto.AdminLimitedDropSummaryResponse;
import com.groove.limited.dto.LimitedDropSummaryRow;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.entity.LimitedDropStatus;

import jakarta.persistence.LockModeType;

public interface LimitedDropRepository extends JpaRepository<LimitedDrop, Long> {

	Optional<LimitedDrop> findByProductId(Long productId);

	boolean existsByProductIdAndStatusNot(Long productId, LimitedDropStatus status);

	@EntityGraph(attributePaths = "product")
	Optional<LimitedDrop> findWithProductById(Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = "product")
	@Query("select d from LimitedDrop d where d.id = :id")
	Optional<LimitedDrop> findByIdForUpdate(@Param("id") Long id);

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

	@Query("""
			SELECT new com.groove.limited.dto.LimitedDropSummaryRow(
				d.id, p.id, p.title, a.name, p.price,
				(SELECT MIN(i.imageUrl) FROM ProductImage i WHERE i.product = p AND i.sortOrder = 0),
				d.totalQuantity, d.soldCount, d.perMemberLimit, d.openAt, d.closeAt, d.status)
			FROM LimitedDrop d JOIN d.product p JOIN p.artist a
			WHERE d.status IN :statuses AND p.status <> com.groove.product.entity.ProductStatus.HIDDEN
			ORDER BY d.openAt ASC, d.id ASC
			""")
	List<LimitedDropSummaryRow> findPublicSummaries(@Param("statuses") Collection<LimitedDropStatus> statuses);
}
