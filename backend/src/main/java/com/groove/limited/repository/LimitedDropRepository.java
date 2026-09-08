package com.groove.limited.repository;

import java.time.LocalDateTime;
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

	// 상세 응답이 product.artist.name 까지 읽으므로 아티스트도 함께 건다.
	@EntityGraph(attributePaths = {"product", "product.artist"})
	Optional<LimitedDrop> findWithProductById(Long id);

	// product 를 fetch join 하면 MySQL 의 for update(of 절 없음)가 조인된 product 행까지 잠근다.
	// performance_schema.data_locks 로 실측: product PRIMARY 에 X,REC_NOT_GAP 락이 잡혀 있었다. 그래서 뗐다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
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

	List<LimitedDrop> findAllByStatusAndOpenAtLessThanEqual(LimitedDropStatus status, LocalDateTime openAt);

	List<LimitedDrop> findAllByStatusInAndCloseAtLessThanEqual(Collection<LimitedDropStatus> statuses,
			LocalDateTime closeAt);
}
