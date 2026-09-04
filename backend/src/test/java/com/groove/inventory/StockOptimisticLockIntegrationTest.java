package com.groove.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.global.common.ApiResponse;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.GlobalExceptionHandler;
import com.groove.inventory.entity.Stock;
import com.groove.inventory.repository.StockRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * 전역 핸들러가 재고 낙관적 락 충돌을 가려내는 기준은 예외에 실린 엔티티명이다.
 * 손으로 만든 예외는 Hibernate 가 실제로 무엇을 싣는지 증명하지 못하므로 진짜 버전 충돌로 확인한다.
 */
class StockOptimisticLockIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

	@Nested
	@DisplayName("재고 버전이 어긋난 채 flush 하면")
	class StaleStockFlush {

		@Test
		@DisplayName("엔티티명이 실린 낙관적 락 예외가 나고 전역 핸들러가 409 STOCK_CONFLICT 로 변환한다")
		void raisesOptimisticLockFailureCarryingStockEntityName() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product product = productRepository.save(ProductFixture.create(artist));
			Stock saved = stockRepository.save(StockFixture.create(product, 10));
			Long stockId = saved.getId();

			// when: 영속 상태로 적재한 뒤 같은 행의 version 을 밀어 UPDATE 가 0 건을 맞게 만든다.
			ObjectOptimisticLockingFailureException thrown = (ObjectOptimisticLockingFailureException)
					assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
						Stock stock = stockRepository.findById(stockId).orElseThrow();
						jdbcTemplate.update("update stock set version = version + 1 where id = ?", stockId);
						stock.increase(1);
						stockRepository.flush();
					}))
							.isInstanceOf(ObjectOptimisticLockingFailureException.class)
							.actual();

			// then: Hibernate 는 엔티티명만 싣는다. getPersistentClass() 로 분기하면 여기서 조용히 빗나간다.
			assertThat(thrown.getPersistentClass()).isNull();
			assertThat(thrown.getPersistentClassName()).isEqualTo(Stock.class.getName());

			ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleOptimisticLock(thrown);
			assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
			assertThat(response.getBody().error().code()).isEqualTo(ErrorCode.STOCK_CONFLICT.name());
		}
	}
}
