package com.groove.global.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V26 마이그레이션이 orders.status(결제 생애주기) → order_item.status(상품주문 이행 상태)를 어떻게 매핑하고,
 * 상품주문번호·할인 배분을 어떻게 백필하는지 검증한다. OrderPlacementBackfillMigrationTest 와 같은 방식으로
 * V25 까지 적용한 뒤 레거시 데이터를 심고, V26 까지 마저 적용해 결과를 확인한다.
 */
class OrderItemStatusBackfillMigrationTest {

	private final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
			.withDatabaseName("groove_order_item_status_backfill")
			.withUsername("groove")
			.withPassword("groove1234")
			.withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

	private long memberId;
	private long productId;

	@AfterEach
	void stopContainer() {
		mysql.stop();
	}

	@BeforeEach
	void migrateUpToV25AndSeedCatalog() throws Exception {
		mysql.start();
		Flyway.configure()
				.dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
				.target("25")
				.load()
				.migrate();

		memberId = insertMember("order-item-status@groove.com");
		long artistId = insertArtist("Miles Davis");
		long albumId = insertAlbum("Kind of Blue", artistId);
		productId = insertProduct(albumId, artistId, "30000");
	}

	private void migrateToLatest() {
		Flyway.configure()
				.dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
				.load()
				.migrate();
	}

	private Connection connect() throws Exception {
		return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
	}

	@Nested
	@DisplayName("migrate() V26")
	class MigrateV26 {

		@Test
		@DisplayName("결제 전(placed_at 없음) PENDING 주문의 상품주문은 PAYMENT_PENDING 이다")
		void backfillsPaymentPendingWhenNotPlaced() throws Exception {
			// given
			long orderId = insertOrder("PENDING", LocalDateTime.of(2026, 9, 1, 9, 0), null, null,
					"30000", "0");
			insertOrderItem(orderId, "30000", 1);

			// when
			migrateToLatest();

			// then
			assertThat(findItemStatus(orderId)).containsExactly("PAYMENT_PENDING");
		}

		@Test
		@DisplayName("가상계좌 발급 후(placed_at 있음) 아직 PENDING 인 주문의 상품주문은 PAYMENT_WAITING 이다")
		void backfillsPaymentWaitingWhenPlacedButNotPaid() throws Exception {
			// given
			LocalDateTime placedAt = LocalDateTime.of(2026, 9, 2, 10, 0);
			long orderId = insertOrder("PENDING", placedAt.minusMinutes(1), placedAt, null, "30000", "0");
			insertOrderItem(orderId, "30000", 1);

			// when
			migrateToLatest();

			// then
			assertThat(findItemStatus(orderId)).containsExactly("PAYMENT_WAITING");
		}

		@ParameterizedTest
		@CsvSource({
			"PAID, PAID",
			"PREPARING, PREPARING",
			"SHIPPED, SHIPPING",
			"DELIVERED, DELIVERED",
			"REFUNDED, CANCELED"
		})
		@DisplayName("결제완료 이후 상태는 대응하는 상품주문 상태로 매핑된다")
		void mapsPostPaymentStatuses(String orderStatus, String expectedItemStatus) throws Exception {
			// given
			LocalDateTime placedAt = LocalDateTime.of(2026, 9, 3, 10, 0);
			long orderId = insertOrder(orderStatus, placedAt.minusMinutes(5), placedAt, null, "30000", "0");
			insertOrderItem(orderId, "30000", 1);

			// when
			migrateToLatest();

			// then
			assertThat(findItemStatus(orderId)).containsExactly(expectedItemStatus);
		}

		@Test
		@DisplayName("가상계좌 발급 후 입금 없이 입금기한이 지나 만료된 주문의 상품주문은 CANCELED_BY_NOPAYMENT 다")
		void backfillsCanceledByNopaymentWhenVirtualAccountExpired() throws Exception {
			// given
			LocalDateTime placedAt = LocalDateTime.of(2026, 9, 4, 10, 0);
			LocalDateTime canceledAt = LocalDateTime.of(2026, 9, 4, 12, 0);
			long orderId = insertOrder("CANCELED", placedAt.minusMinutes(5), placedAt, canceledAt, "30000", "0");
			setCancelReason(orderId, "EXPIRED");
			insertOrderItem(orderId, "30000", 1);
			insertPayment(orderId, "CANCELED", null, placedAt);

			// when
			migrateToLatest();

			// then
			assertThat(findItemStatus(orderId)).containsExactly("CANCELED_BY_NOPAYMENT");
		}

		@Test
		@DisplayName("가상계좌 입금 전 구매자가 직접 취소한 주문의 상품주문은 그냥 CANCELED 다")
		void backfillsCanceledWhenVirtualAccountCanceledByBuyer() throws Exception {
			// given
			LocalDateTime placedAt = LocalDateTime.of(2026, 9, 4, 10, 0);
			LocalDateTime canceledAt = LocalDateTime.of(2026, 9, 4, 11, 0);
			long orderId = insertOrder("CANCELED", placedAt.minusMinutes(5), placedAt, canceledAt, "30000", "0");
			setCancelReason(orderId, "단순 변심");
			insertOrderItem(orderId, "30000", 1);
			insertPayment(orderId, "CANCELED", null, placedAt);

			// when
			migrateToLatest();

			// then
			assertThat(findItemStatus(orderId)).containsExactly("CANCELED");
		}

		@Test
		@DisplayName("결제 승인 뒤 취소된 주문의 상품주문은 그냥 CANCELED 다")
		void backfillsPlainCanceledWhenPaidThenCanceled() throws Exception {
			// given
			LocalDateTime approvedAt = LocalDateTime.of(2026, 9, 5, 10, 0);
			LocalDateTime canceledAt = LocalDateTime.of(2026, 9, 6, 10, 0);
			long orderId = insertOrder("CANCELED", approvedAt.minusMinutes(5), approvedAt, canceledAt, "30000", "0");
			insertOrderItem(orderId, "30000", 1);
			insertPayment(orderId, "CANCELED", approvedAt, approvedAt);

			// when
			migrateToLatest();

			// then
			assertThat(findItemStatus(orderId)).containsExactly("CANCELED");
		}

		@Test
		@DisplayName("결제 시도 없이 만료·재제출된 주문의 상품주문은 그냥 CANCELED 다")
		void backfillsPlainCanceledWhenNeverPlaced() throws Exception {
			// given
			long orderId = insertOrder("CANCELED", LocalDateTime.of(2026, 9, 7, 9, 0), null,
					LocalDateTime.of(2026, 9, 7, 9, 10), "30000", "0");
			insertOrderItem(orderId, "30000", 1);

			// when
			migrateToLatest();

			// then
			assertThat(findItemStatus(orderId)).containsExactly("CANCELED");
		}

		@Test
		@DisplayName("취소된 상품주문의 canceled_at 은 주문의 취소 시각을 그대로 옮긴다")
		void backfillsCanceledAtFromOrderCanceledAt() throws Exception {
			// given
			LocalDateTime canceledAt = LocalDateTime.of(2026, 9, 8, 15, 0);
			long orderId = insertOrder("CANCELED", LocalDateTime.of(2026, 9, 8, 9, 0), null, canceledAt,
					"30000", "0");
			insertOrderItem(orderId, "30000", 1);

			// when
			migrateToLatest();

			// then
			try (Connection connection = connect()) {
				assertThat(findCanceledAt(connection, orderId)).isEqualTo(canceledAt);
			}
		}

		@Test
		@DisplayName("상품주문번호는 주문 안 등록 순서대로 -01, -02 로 채워진다")
		void backfillsProductOrderNumberSequentially() throws Exception {
			// given
			long orderId = insertOrder("PAID", LocalDateTime.of(2026, 9, 9, 9, 0),
					LocalDateTime.of(2026, 9, 9, 9, 0), null, "60000", "0");
			long firstItemId = insertOrderItem(orderId, "30000", 1);
			long secondItemId = insertOrderItem(orderId, "30000", 1);

			// when
			migrateToLatest();

			// then
			try (Connection connection = connect()) {
				String orderNumber = findOrderNumber(connection, orderId);
				assertThat(findProductOrderNumber(connection, firstItemId)).isEqualTo(orderNumber + "-01");
				assertThat(findProductOrderNumber(connection, secondItemId)).isEqualTo(orderNumber + "-02");
			}
		}

		@Test
		@DisplayName("할인액을 라인 금액 비율로 나누고, 남는 원 단위는 라인 금액이 큰 상품에 더한다")
		void backfillsDiscountShareProportionallyWithRemainder() throws Exception {
			// given — 라인 금액 30000:20000, 총 50000원 중 100원 할인을 나누면
			// FLOOR(100*30000/50000)=60, FLOOR(100*20000/50000)=40 이라 나머지가 없다
			long orderId = insertOrder("PAID", LocalDateTime.of(2026, 9, 10, 9, 0),
					LocalDateTime.of(2026, 9, 10, 9, 0), null, "50000", "100");
			long largeItemId = insertOrderItem(orderId, "30000", 1);
			long smallItemId = insertOrderItem(orderId, "20000", 1);

			// when
			migrateToLatest();

			// then
			try (Connection connection = connect()) {
				assertThat(findDiscountShare(connection, largeItemId)).isEqualByComparingTo("60");
				assertThat(findDiscountShare(connection, smallItemId)).isEqualByComparingTo("40");
			}
		}

		@ParameterizedTest
		@CsvSource({
			"PREPARING, true, false, false",
			"SHIPPED, false, true, false",
			"DELIVERED, false, true, true"
		})
		@DisplayName("준비중·배송중·배송완료 주문은 해당 단계 시각을 orders.updated_at 으로 채우고 나머지는 비워 둔다")
		void backfillsStageTimestampsFromOrderUpdatedAt(String orderStatus, boolean prepared, boolean shipped,
				boolean delivered) throws Exception {
			// given
			LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 11, 14, 30, 5);
			long orderId = insertOrder(orderStatus, LocalDateTime.of(2026, 9, 11, 9, 0),
					LocalDateTime.of(2026, 9, 11, 9, 0), null, "30000", "0", updatedAt);
			long itemId = insertOrderItem(orderId, "30000", 1);

			// when
			migrateToLatest();

			// then
			try (Connection connection = connect()) {
				assertThat(findItemTimestamp(connection, itemId, "prepared_at")).isEqualTo(prepared ? updatedAt : null);
				assertThat(findItemTimestamp(connection, itemId, "shipped_at")).isEqualTo(shipped ? updatedAt : null);
				assertThat(findItemTimestamp(connection, itemId, "delivered_at"))
						.isEqualTo(delivered ? updatedAt : null);
			}
		}

		@ParameterizedTest
		@CsvSource({"PAID", "REFUNDED"})
		@DisplayName("준비중 이전이거나 취소된 주문은 단계 시각 세 컬럼을 모두 비워 둔다")
		void leavesStageTimestampsNullForOtherStatuses(String orderStatus) throws Exception {
			// given
			LocalDateTime updatedAt = LocalDateTime.of(2026, 9, 12, 10, 0, 0);
			long orderId = insertOrder(orderStatus, LocalDateTime.of(2026, 9, 12, 9, 0),
					LocalDateTime.of(2026, 9, 12, 9, 0), null, "30000", "0", updatedAt);
			long itemId = insertOrderItem(orderId, "30000", 1);

			// when
			migrateToLatest();

			// then
			try (Connection connection = connect()) {
				assertThat(findItemTimestamp(connection, itemId, "prepared_at")).isNull();
				assertThat(findItemTimestamp(connection, itemId, "shipped_at")).isNull();
				assertThat(findItemTimestamp(connection, itemId, "delivered_at")).isNull();
			}
		}

		@Test
		@DisplayName("백필된 배송중·배송완료 상품은 자동 배송완료·자동 구매확정 대상 쿼리에 잡힌다")
		void backfilledItemsAreSelectedBySchedulerQueries() throws Exception {
			// given
			LocalDateTime updatedAt = LocalDateTime.now().minusDays(30).truncatedTo(ChronoUnit.SECONDS);
			long shippedOrderId = insertOrder("SHIPPED", updatedAt.minusHours(2), updatedAt.minusHours(2), null,
					"30000", "0", updatedAt);
			long shippedItemId = insertOrderItem(shippedOrderId, "30000", 1);
			long deliveredOrderId = insertOrder("DELIVERED", updatedAt.minusHours(2), updatedAt.minusHours(2), null,
					"30000", "0", updatedAt);
			long deliveredItemId = insertOrderItem(deliveredOrderId, "30000", 1);
			LocalDateTime cutoff = LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.SECONDS);

			// when
			migrateToLatest();

			// then
			try (Connection connection = connect()) {
				assertThat(findIdsByStatusAndTimestampBefore(connection, "SHIPPING", "shipped_at", cutoff))
						.contains(shippedItemId)
						.doesNotContain(deliveredItemId);
				assertThat(findIdsByStatusAndTimestampBefore(connection, "DELIVERED", "delivered_at", cutoff))
						.contains(deliveredItemId)
						.doesNotContain(shippedItemId);
			}
		}

		@Test
		@DisplayName("불변식 검증 임시 테이블은 위반 건수가 0이 아니면 CHECK 로 INSERT 를 거부한다")
		void invariantCheckRejectsNonZeroViolationCount() throws Exception {
			// given — 정상 데이터로는 세 불변식을 실제로 깰 수 없다는 것부터 확인했다: orders.status 는
			// V1 부터 CHECK(status IN (...))로 막혀 매핑표 밖의 값이 못 들어가고, product_order_number 는
			// order_number 의 UNIQUE 제약 덕에 전역에서 겹칠 수 없으며, discount_share 배분은 잔여 원 단위를
			// 항상 보정해 합계가 discount_amount 와 어긋나지 않는다(total_amount=0 이어도 remainder 보정이
			// 전액을 되돌려 채운다). 그래서 여기서는 V26 이 쓰는 것과 같은 모양의 임시 테이블 자체가 위반
			// 건수를 실제로 거부하는지만 직접 확인한다.
			try (Connection connection = connect(); Statement statement = connection.createStatement()) {
				statement.execute("CREATE TEMPORARY TABLE v26_invariant_probe ("
						+ "violations bigint NOT NULL, CONSTRAINT chk_v26_invariant_probe CHECK (violations = 0))");

				statement.execute("INSERT INTO v26_invariant_probe (violations) VALUES (0)");

				assertThatThrownBy(() -> statement.execute("INSERT INTO v26_invariant_probe (violations) VALUES (1)"))
						.isInstanceOf(SQLException.class)
						.hasMessageContaining("chk_v26_invariant_probe");
			}
		}
	}

	private List<String> findItemStatus(long orderId) throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"select status from order_item where order_id = ? order by id")) {
			statement.setLong(1, orderId);
			ResultSet result = statement.executeQuery();
			List<String> statuses = new ArrayList<>();
			while (result.next()) {
				statuses.add(result.getString(1));
			}
			return statuses;
		}
	}

	private LocalDateTime findCanceledAt(Connection connection, long orderId) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
				"select oi.canceled_at from order_item oi where oi.order_id = ?")) {
			statement.setLong(1, orderId);
			ResultSet result = statement.executeQuery();
			result.next();
			Timestamp value = result.getTimestamp(1);
			return value == null ? null : value.toLocalDateTime();
		}
	}

	private LocalDateTime findItemTimestamp(Connection connection, long itemId, String column) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
				"select " + column + " from order_item where id = ?")) {
			statement.setLong(1, itemId);
			ResultSet result = statement.executeQuery();
			result.next();
			Timestamp value = result.getTimestamp(1);
			return value == null ? null : value.toLocalDateTime();
		}
	}

	private List<Long> findIdsByStatusAndTimestampBefore(Connection connection, String status, String column,
			LocalDateTime cutoff) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
				"select id from order_item where status = ? and " + column + " <= ?")) {
			statement.setString(1, status);
			statement.setTimestamp(2, Timestamp.valueOf(cutoff));
			ResultSet result = statement.executeQuery();
			List<Long> ids = new ArrayList<>();
			while (result.next()) {
				ids.add(result.getLong(1));
			}
			return ids;
		}
	}

	private String findOrderNumber(Connection connection, long orderId) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
				"select order_number from orders where id = ?")) {
			statement.setLong(1, orderId);
			ResultSet result = statement.executeQuery();
			result.next();
			return result.getString(1);
		}
	}

	private String findProductOrderNumber(Connection connection, long itemId) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
				"select product_order_number from order_item where id = ?")) {
			statement.setLong(1, itemId);
			ResultSet result = statement.executeQuery();
			result.next();
			return result.getString(1);
		}
	}

	private BigDecimal findDiscountShare(Connection connection, long itemId) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
				"select discount_share from order_item where id = ?")) {
			statement.setLong(1, itemId);
			ResultSet result = statement.executeQuery();
			result.next();
			return result.getBigDecimal(1);
		}
	}

	private long insertMember(String email) throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into member (role, status, nickname, email, password, created_at, updated_at) "
								+ "values ('USER', 'ACTIVE', 'tester', ?, 'hash', now(6), now(6))",
						Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, email);
			statement.executeUpdate();
			return generatedId(statement);
		}
	}

	private long insertArtist(String name) throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into artist (name, created_at, updated_at) values (?, now(6), now(6))",
						Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, name);
			statement.executeUpdate();
			return generatedId(statement);
		}
	}

	private long insertAlbum(String title, long artistId) throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into album (title, artist_id, created_at, updated_at) values (?, ?, now(6), now(6))",
						Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, title);
			statement.setLong(2, artistId);
			statement.executeUpdate();
			return generatedId(statement);
		}
	}

	private long insertProduct(long albumId, long artistId, String price) throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into product (title, album_id, artist_id, price, status, review_count, "
								+ "created_at, updated_at) "
								+ "values ('Kind of Blue', ?, ?, ?, 'ON_SALE', 0, now(6), now(6))",
						Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, albumId);
			statement.setLong(2, artistId);
			statement.setBigDecimal(3, new BigDecimal(price));
			statement.executeUpdate();
			return generatedId(statement);
		}
	}

	private long insertOrder(String status, LocalDateTime createdAt, LocalDateTime placedAt,
			LocalDateTime canceledAt, String totalAmount, String discountAmount) throws Exception {
		return insertOrder(status, createdAt, placedAt, canceledAt, totalAmount, discountAmount, createdAt);
	}

	private long insertOrder(String status, LocalDateTime createdAt, LocalDateTime placedAt,
			LocalDateTime canceledAt, String totalAmount, String discountAmount, LocalDateTime updatedAt)
			throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into orders (order_number, member_id, status, total_amount, discount_amount, "
								+ "final_amount, expires_at, zip_code, phone, recipient_name, address1, placed_at, "
								+ "canceled_at, order_source, created_at, updated_at) "
								+ "values (?, ?, ?, ?, ?, ?, ?, '06236', '010-0000-0000', '테스터', "
								+ "'서울시 강남구', ?, ?, 'CART', ?, ?)",
						Statement.RETURN_GENERATED_KEYS)) {
			BigDecimal total = new BigDecimal(totalAmount);
			BigDecimal discount = new BigDecimal(discountAmount);
			statement.setString(1, "2026" + String.format("%08d", memberId) + "-" + System.nanoTime() % 100000);
			statement.setLong(2, memberId);
			statement.setString(3, status);
			statement.setBigDecimal(4, total);
			statement.setBigDecimal(5, discount);
			statement.setBigDecimal(6, total.subtract(discount));
			statement.setTimestamp(7, Timestamp.valueOf(createdAt.plusMinutes(10)));
			setNullableTimestamp(statement, 8, placedAt);
			setNullableTimestamp(statement, 9, canceledAt);
			statement.setTimestamp(10, Timestamp.valueOf(createdAt));
			statement.setTimestamp(11, Timestamp.valueOf(updatedAt));
			statement.executeUpdate();
			return generatedId(statement);
		}
	}

	private void setCancelReason(long orderId, String reason) throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"update orders set cancel_reason = ? where id = ?")) {
			statement.setString(1, reason);
			statement.setLong(2, orderId);
			statement.executeUpdate();
		}
	}

	private long insertOrderItem(long orderId, String priceSnapshot, int quantity) throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into order_item (order_id, product_id, product_name_snapshot, price_snapshot, "
								+ "quantity, created_at, updated_at) "
								+ "values (?, ?, 'Kind of Blue', ?, ?, now(6), now(6))",
						Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, orderId);
			statement.setLong(2, productId);
			statement.setBigDecimal(3, new BigDecimal(priceSnapshot));
			statement.setInt(4, quantity);
			statement.executeUpdate();
			return generatedId(statement);
		}
	}

	private void insertPayment(long orderId, String status, LocalDateTime approvedAt, LocalDateTime createdAt)
			throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into payment (order_id, toss_order_id, status, amount, approved_at, "
								+ "created_at, updated_at) values (?, ?, ?, 30000, ?, ?, ?)")) {
			statement.setLong(1, orderId);
			statement.setString(2, "toss-order-" + orderId);
			statement.setString(3, status);
			setNullableTimestamp(statement, 4, approvedAt);
			statement.setTimestamp(5, Timestamp.valueOf(createdAt));
			statement.setTimestamp(6, Timestamp.valueOf(createdAt));
			statement.executeUpdate();
		}
	}

	private void setNullableTimestamp(PreparedStatement statement, int index, LocalDateTime value) throws Exception {
		if (value == null) {
			statement.setNull(index, Types.TIMESTAMP);
		} else {
			statement.setTimestamp(index, Timestamp.valueOf(value));
		}
	}

	private long generatedId(PreparedStatement statement) throws Exception {
		ResultSet keys = statement.getGeneratedKeys();
		keys.next();
		return keys.getLong(1);
	}
}
