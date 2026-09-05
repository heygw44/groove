package com.groove.recommend.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.recommend.entity.ProductViewLog;

public interface ProductViewLogRepository extends JpaRepository<ProductViewLog, Long> {

	// JPQL 은 LIMIT 를 지원하지 않아 배치 삭제를 네이티브 쿼리로 작성한다.
	@Modifying(clearAutomatically = true)
	@Query(value = "DELETE FROM product_view_log WHERE viewed_at < :threshold ORDER BY id LIMIT :size",
			nativeQuery = true)
	int deleteExpired(@Param("threshold") LocalDateTime threshold, @Param("size") int size);
}
