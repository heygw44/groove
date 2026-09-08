package com.groove.limited;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.LimitedDropFixture;
import com.groove.fixture.ProductFixture;
import com.groove.fixture.StockFixture;
import com.groove.inventory.repository.StockRepository;
import com.groove.limited.entity.LimitedDrop;
import com.groove.limited.repository.LimitedDropRepository;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Product;
import com.groove.product.repository.AlbumRepository;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.ProductRepository;
import com.groove.support.IntegrationTestSupport;

/**
 * findByIdForUpdate() 가 실제로 어느 테이블 행을 잠그는지 performance_schema.data_locks 로 확인한다.
 * product 를 fetch join 하면 for update(of 절 없음)가 조인된 product 행까지 잠그는 것을 실측으로 재현했던 문제라
 * 회귀 방지 목적으로 남긴다.
 */
class LimitedDropLockScopeIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private AlbumRepository albumRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private LimitedDropRepository limitedDropRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void grantDataLocksPrivilege() throws Exception {
		String url;
		try (Connection appConnection = jdbcTemplate.getDataSource().getConnection()) {
			url = appConnection.getMetaData().getURL();
		}
		// IntegrationTestSupport 의 MySQLContainer 는 root 패스워드를 app 유저 패스워드와 동일하게 맞춘다.
		try (Connection rootConnection = DriverManager.getConnection(url, "root", "groove1234")) {
			rootConnection.createStatement().execute("GRANT PROCESS, SELECT ON *.* TO 'groove'@'%'");
			rootConnection.createStatement().execute("GRANT SELECT ON performance_schema.* TO 'groove'@'%'");
			rootConnection.createStatement().execute("FLUSH PRIVILEGES");
		}
	}

	@Nested
	@DisplayName("findByIdForUpdate()")
	class FindByIdForUpdate {

		@Test
		@DisplayName("락을 쥔 채 대기해도 limited_drop 행만 잠기고 product 행은 잠기지 않는다")
		void locksOnlyLimitedDropRowNotProductRow() throws Exception {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create());
			Product createdProduct = ProductFixture.create(artist);
			albumRepository.save(createdProduct.getAlbum());
			Product product = productRepository.save(createdProduct);
			stockRepository.saveAndFlush(StockFixture.create(product, 10));
			LimitedDrop drop = limitedDropRepository.saveAndFlush(LimitedDropFixture.scheduled(product));
			Long dropId = drop.getId();

			CountDownLatch lockAcquired = new CountDownLatch(1);
			CountDownLatch releaseLock = new CountDownLatch(1);
			List<Throwable> failures = new ArrayList<>();

			TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
			Thread holder = new Thread(() -> {
				try {
					txTemplate.execute(status -> {
						limitedDropRepository.findByIdForUpdate(dropId).orElseThrow();
						lockAcquired.countDown();
						try {
							releaseLock.await(10, TimeUnit.SECONDS);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						return null;
					});
				} catch (Throwable t) {
					failures.add(t);
					lockAcquired.countDown();
				}
			});
			holder.start();

			boolean acquired = lockAcquired.await(10, TimeUnit.SECONDS);
			assertThat(acquired).isTrue();
			assertThat(failures).isEmpty();

			// when: 다른 커넥션에서 data_locks 조회
			List<Map<String, Object>> locks = jdbcTemplate.queryForList(
					"SELECT OBJECT_NAME, INDEX_NAME, LOCK_TYPE, LOCK_MODE "
							+ "FROM performance_schema.data_locks "
							+ "WHERE OBJECT_SCHEMA = DATABASE() AND LOCK_TYPE = 'RECORD' "
							+ "ORDER BY OBJECT_NAME");

			releaseLock.countDown();
			holder.join(10_000);

			// then
			List<String> lockedTables = locks.stream().map(row -> (String) row.get("OBJECT_NAME")).distinct()
					.toList();
			assertThat(lockedTables).containsExactly("limited_drop");
		}
	}
}
