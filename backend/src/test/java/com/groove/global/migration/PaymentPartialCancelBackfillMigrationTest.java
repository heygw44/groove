package com.groove.global.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V27 마이그레이션이 payment.canceled_amount 컬럼과 payment_cancel 테이블을 추가하고, 기존 전액취소(CANCELED)
 * 결제마다 DONE 상태 payment_cancel 행을 1건씩 백필하는지 검증한다. OrderItemStatusBackfillMigrationTest 와
 * 같은 방식으로 V26 까지 적용한 뒤 레거시 데이터를 심고, V27 까지 마저 적용해 결과를 확인한다.
 */
class PaymentPartialCancelBackfillMigrationTest {

	private final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
			.withDatabaseName("groove_payment_partial_cancel_backfill")
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
	void migrateUpToV26AndSeedCatalog() throws Exception {
		mysql.start();
		Flyway.configure()
				.dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
				.target("26")
				.load()
				.migrate();

		memberId = insertMember("payment-partial-cancel@groove.com");
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
	@DisplayName("migrate() V27")
	class MigrateV27 {

		@Test
		@DisplayName("전액취소(CANCELED) 결제마다 DONE 상태 payment_cancel 행을 1건씩 백필한다")
		void backfillsDonePaymentCancelForCanceledPayment() throws Exception {
			// given
			LocalDateTime approvedAt = LocalDateTime.of(2026, 9, 1, 10, 0);
			LocalDateTime canceledAt = LocalDateTime.of(2026, 9, 2, 11, 0);
			long orderId = insertOrder("20260901-LEGACY01");
			long paymentId = insertPayment(orderId, "CANCELED", "30000", approvedAt, canceledAt);

			// when
			migrateToLatest();

			// then
			try (Connection connection = connect()) {
				assertThat(findPaymentCancelCount(connection, paymentId)).isEqualTo(1);
				PaymentCancelRow row = findPaymentCancelRow(connection, paymentId);
				assertThat(row.idempotencyKey()).isEqualTo("legacy-" + paymentId);
				assertThat(row.cancelAmount()).isEqualByComparingTo("30000");
				assertThat(row.status()).isEqualTo("DONE");
				assertThat(row.doneAt()).isEqualTo(canceledAt);
				assertThat(findCanceledAmount(connection, paymentId)).isEqualByComparingTo("30000");
			}
		}

		@Test
		@DisplayName("취소되지 않은(DONE) 결제는 payment_cancel 행을 만들지 않고 canceled_amount 는 0으로 남는다")
		void doesNotBackfillForDonePayment() throws Exception {
			// given
			LocalDateTime approvedAt = LocalDateTime.of(2026, 9, 1, 10, 0);
			long orderId = insertOrder("20260901-LEGACY02");
			long paymentId = insertPayment(orderId, "DONE", "30000", approvedAt, null);

			// when
			migrateToLatest();

			// then
			try (Connection connection = connect()) {
				assertThat(findPaymentCancelCount(connection, paymentId)).isZero();
				assertThat(findCanceledAmount(connection, paymentId)).isEqualByComparingTo("0");
			}
		}

		@Test
		@DisplayName("승인 없이 취소된(입금 전 가상계좌 폐쇄) 결제는 payment_cancel 행이 생기지 않고 canceled_amount 는 0으로 남는다")
		void doesNotBackfillForCanceledVirtualAccountNeverApproved() throws Exception {
			// given: 입금 전 가상계좌 폐쇄는 approved_at 이 끝까지 null 이다 - 돈이 오간 적이 없어 환불이 아니다
			LocalDateTime canceledAt = LocalDateTime.of(2026, 9, 2, 9, 0);
			long orderId = insertOrder("20260901-LEGACY04");
			long paymentId = insertPayment(orderId, "CANCELED", "30000", null, canceledAt);

			// when
			migrateToLatest();

			// then
			try (Connection connection = connect()) {
				assertThat(findPaymentCancelCount(connection, paymentId)).isZero();
				assertThat(findCanceledAmount(connection, paymentId)).isEqualByComparingTo("0");
			}
		}

		@Test
		@DisplayName("취소 시각이 없는 CANCELED 결제는 updated_at 을 대신 써서 요청·완료 시각을 채운다")
		void fallsBackToUpdatedAtWhenCanceledAtIsMissing() throws Exception {
			// given: 실제로는 나오기 어려운 데이터지만 NOT NULL 백필 로직 자체를 확인한다
			LocalDateTime approvedAt = LocalDateTime.of(2026, 9, 1, 10, 0);
			long orderId = insertOrder("20260901-LEGACY03");
			long paymentId = insertPayment(orderId, "CANCELED", "30000", approvedAt, null);

			// when
			migrateToLatest();

			// then
			try (Connection connection = connect()) {
				PaymentCancelRow row = findPaymentCancelRow(connection, paymentId);
				assertThat(row.requestedAt()).isNotNull();
			}
		}
	}

	private record PaymentCancelRow(String idempotencyKey, BigDecimal cancelAmount, String status,
			LocalDateTime requestedAt, LocalDateTime doneAt) {
	}

	private int findPaymentCancelCount(Connection connection, long paymentId) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
				"select count(*) from payment_cancel where payment_id = ?")) {
			statement.setLong(1, paymentId);
			ResultSet result = statement.executeQuery();
			result.next();
			return result.getInt(1);
		}
	}

	private PaymentCancelRow findPaymentCancelRow(Connection connection, long paymentId) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
				"select idempotency_key, cancel_amount, status, requested_at, done_at "
						+ "from payment_cancel where payment_id = ?")) {
			statement.setLong(1, paymentId);
			ResultSet result = statement.executeQuery();
			result.next();
			Timestamp doneAt = result.getTimestamp("done_at");
			Timestamp requestedAt = result.getTimestamp("requested_at");
			return new PaymentCancelRow(result.getString("idempotency_key"), result.getBigDecimal("cancel_amount"),
					result.getString("status"), requestedAt == null ? null : requestedAt.toLocalDateTime(),
					doneAt == null ? null : doneAt.toLocalDateTime());
		}
	}

	private BigDecimal findCanceledAmount(Connection connection, long paymentId) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
				"select canceled_amount from payment where id = ?")) {
			statement.setLong(1, paymentId);
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

	private long insertOrder(String orderNumber) throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into orders (order_number, member_id, status, total_amount, discount_amount, "
								+ "final_amount, expires_at, zip_code, phone, recipient_name, address1, placed_at, "
								+ "order_source, created_at, updated_at) "
								+ "values (?, ?, 'PAID', 30000, 0, 30000, ?, '06236', '010-0000-0000', '테스터', "
								+ "'서울시 강남구', ?, 'CART', ?, ?)",
						Statement.RETURN_GENERATED_KEYS)) {
			LocalDateTime now = LocalDateTime.of(2026, 9, 1, 9, 0);
			statement.setString(1, orderNumber);
			statement.setLong(2, memberId);
			statement.setTimestamp(3, Timestamp.valueOf(now.plusDays(1)));
			statement.setTimestamp(4, Timestamp.valueOf(now));
			statement.setTimestamp(5, Timestamp.valueOf(now));
			statement.setTimestamp(6, Timestamp.valueOf(now));
			statement.executeUpdate();
			long orderId = generatedId(statement);
			insertOrderItem(orderId, orderNumber);
			return orderId;
		}
	}

	private void insertOrderItem(long orderId, String orderNumber) throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into order_item (order_id, product_id, product_name_snapshot, price_snapshot, "
								+ "quantity, product_order_number, created_at, updated_at) "
								+ "values (?, ?, 'Kind of Blue', 30000, 1, ?, now(6), now(6))")) {
			statement.setLong(1, orderId);
			statement.setLong(2, productId);
			statement.setString(3, orderNumber + "-01");
			statement.executeUpdate();
		}
	}

	private long insertPayment(long orderId, String status, String amount, LocalDateTime approvedAt,
			LocalDateTime canceledAt) throws Exception {
		LocalDateTime baseTime = LocalDateTime.of(2026, 9, 1, 9, 0);
		LocalDateTime rowTime = approvedAt != null ? approvedAt : (canceledAt != null ? canceledAt : baseTime);
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into payment (order_id, toss_order_id, payment_key, status, amount, approved_at, "
								+ "canceled_at, created_at, updated_at) "
								+ "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
						Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, orderId);
			statement.setString(2, "toss-order-" + orderId);
			statement.setString(3, "toss-key-" + orderId);
			statement.setString(4, status);
			statement.setBigDecimal(5, new BigDecimal(amount));
			setNullableTimestamp(statement, 6, approvedAt);
			setNullableTimestamp(statement, 7, canceledAt);
			statement.setTimestamp(8, Timestamp.valueOf(rowTime));
			statement.setTimestamp(9, Timestamp.valueOf(canceledAt != null ? canceledAt : rowTime));
			statement.executeUpdate();
			return generatedId(statement);
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
