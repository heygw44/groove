package com.groove.inventory.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.groove.inventory.entity.Stock;

import jakarta.persistence.LockModeType;

public interface StockRepository extends JpaRepository<Stock, Long> {

	Optional<Stock> findByProductId(Long productId);

	@EntityGraph(attributePaths = "product")
	Optional<Stock> findWithProductByProductId(Long productId);

	List<Stock> findAllByProductIdIn(Collection<Long> productIds);

	// productId 오름차순 고정 정렬로 여러 상품을 동시에 주문하는 요청 간 락 순서를 맞춰 데드락을 방지한다.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@EntityGraph(attributePaths = "product")
	@Query("select s from Stock s where s.product.id in :productIds order by s.product.id")
	List<Stock> findAllWithProductByProductIdInForUpdate(@Param("productIds") Collection<Long> productIds);

	// 같은 트랜잭션의 다른 엔티티가 detach 되면 안 되므로 clearAutomatically 는 쓰지 않는다.
	@Modifying
	@Query("update Stock s set s.quantity = s.quantity - :quantity, s.version = s.version + 1 "
			+ "where s.product.id = :productId and s.quantity >= :quantity")
	int decreaseIfAvailable(@Param("productId") Long productId, @Param("quantity") int quantity);
}
