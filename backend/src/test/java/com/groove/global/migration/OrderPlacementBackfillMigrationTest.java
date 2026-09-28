package com.groove.global.migration;

import static org.assertj.core.api.Assertions.assertThat;

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
 * V25 마이그레이션이 결제 전 주문을 어떻게 숨기고(placed_at) 출처(order_source)를 백필하는지 검증한다.
 * AlbumBackfillMigrationTest 와 같은 방식으로 Spring 컨텍스트 없이 Flyway API 만으로 V24 까지 적용한 뒤
 * 레거시 데이터를 심고, V25 까지 마저 적용해 결과를 확인한다.
 */
class OrderPlacementBackfillMigrationTest {

	private final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
			.withDatabaseName("groove_order_placement_backfill")
			.withUsername("groove")
			.withPassword("groove1234")
			.withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

	private long memberId;
	private long artistId;
	private long albumId;
	private long productId;

	@AfterEach
	void stopContainer() {
		mysql.stop();
	}

	@BeforeEach
	void migrateUpToV24AndSeedCatalog() throws Exception {
		mysql.start();
		Flyway.configure()
				.dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
				.target("24")
				.load()
				.migrate();

		memberId = insertMember("order-placement@groove.com");
		artistId = insertArtist("Miles Davis");
		albumId = insertAlbum("Kind of Blue", artistId);
		productId = insertProduct(albumId, artistId);
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
	@DisplayName("migrate() V25")
	class MigrateV25 {

		@Test
		@DisplayName("결제 승인 이력이 있는 주문은 승인 시각을 확정 시점으로 백필한다")
		void backfillsPlacedAtFromPaymentApprovedAt() throws Exception {
			// given
			LocalDateTime approvedAt = LocalDateTime.of(2026, 9, 1, 10, 0);
			long orderId = insertOrder("PAID", LocalDateTime.of(2026, 9, 1, 9, 55));
			insertPayment(orderId, "DONE", approvedAt, null, null);

			// when
			migrateToLatest();

			// then
			try (Connection connection = connect()) {
				assertThat(findPlacedAt(connection, orderId)).isEqualTo(approvedAt);
				assertThat(findOrderSource(connection, orderId)).isEqualTo("CART");
			}
		}

		@Test
		@DisplayName("결제완료 이후 상태인데 승인 이력이 없으면 주문 생성 시각을 확정 시점으로 백필한다")
		void backfillsPlacedAtFromCreatedAtWhenNoApprovalRecorded() throws Exception {
			// given
			LocalDateTime createdAt = LocalDateTime.of(2026, 9, 2, 12, 0);
			long orderId = insertOrder("PAID", createdAt);

			// when
			migrateToLatest();

			// then
			try (Connection connection = connect()) {
				assertThat(findPlacedAt(connection, orderId)).isEqualTo(createdAt);
			}
		}

		@Test
		@DisplayName("입금 전 가상계좌 주문은 발급 시각을 확정 시점으로 백필한다")
		void backfillsPlacedAtFromVirtualAccountIssuance() throws Exception {
			// given
			LocalDateTime issuedAt = LocalDateTime.of(2026, 9, 3, 15, 30);
			long orderId = insertOrder("PENDING", LocalDateTime.of(2026, 9, 3, 15, 29));
			insertPayment(orderId, "WAITING_FOR_DEPOSIT", null, issuedAt, "088");

			// when
			migrateToLatest();

			// then
			try (Connection connection = connect()) {
				assertThat(findPlacedAt(connection, orderId)).isEqualTo(issuedAt);
			}
		}

		@Test
		@DisplayName("결제 전 주문은 확정 시점이 비어 있다")
		void leavesPlacedAtNullForUnpaidOrder() throws Exception {
			// given
			long orderId = insertOrder("PENDING", LocalDateTime.of(2026, 9, 4, 9, 0));

			// when
			migrateToLatest();

			// then
			try (Connection connection = connect()) {
				assertThat(findPlacedAt(connection, orderId)).isNull();
				assertThat(findOrderSource(connection, orderId)).isEqualTo("CART");
			}
		}

		@Test
		@DisplayName("한정반 구매로 생긴 주문은 결제 확정 여부와 무관하게 출처가 LIMITED 다")
		void backfillsOrderSourceForLimitedPurchase() throws Exception {
			// given
			long orderId = insertOrder("PENDING", LocalDateTime.of(2026, 9, 5, 9, 0));
			long dropId = insertLimitedDrop(productId);
			insertLimitedPurchase(dropId, memberId, orderId);

			// when
			migrateToLatest();

			// then
			try (Connection connection = connect()) {
				assertThat(findOrderSource(connection, orderId)).isEqualTo("LIMITED");
			}
		}
	}

	private LocalDateTime findPlacedAt(Connection connection, long orderId) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
				"select placed_at from orders where id = ?")) {
			statement.setLong(1, orderId);
			ResultSet result = statement.executeQuery();
			result.next();
			Timestamp placedAt = result.getTimestamp(1);
			return placedAt == null ? null : placedAt.toLocalDateTime();
		}
	}

	private String findOrderSource(Connection connection, long orderId) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
				"select order_source from orders where id = ?")) {
			statement.setLong(1, orderId);
			ResultSet result = statement.executeQuery();
			result.next();
			return result.getString(1);
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

	private long insertProduct(long albumId, long artistId) throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into product (title, album_id, artist_id, price, status, review_count, "
								+ "created_at, updated_at) "
								+ "values ('Kind of Blue', ?, ?, 30000, 'ON_SALE', 0, now(6), now(6))",
						Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, albumId);
			statement.setLong(2, artistId);
			statement.executeUpdate();
			return generatedId(statement);
		}
	}

	private long insertOrder(String status, LocalDateTime createdAt) throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into orders (order_number, member_id, status, total_amount, discount_amount, "
								+ "final_amount, expires_at, zip_code, phone, recipient_name, address1, "
								+ "created_at, updated_at) "
								+ "values (?, ?, ?, 30000, 0, 30000, ?, '06236', '010-0000-0000', '테스터', "
								+ "'서울시 강남구', ?, ?)",
						Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, "20260901-" + System.nanoTime());
			statement.setLong(2, memberId);
			statement.setString(3, status);
			statement.setTimestamp(4, Timestamp.valueOf(createdAt.plusMinutes(10)));
			statement.setTimestamp(5, Timestamp.valueOf(createdAt));
			statement.setTimestamp(6, Timestamp.valueOf(createdAt));
			statement.executeUpdate();
			return generatedId(statement);
		}
	}

	private void insertPayment(long orderId, String status, LocalDateTime approvedAt, LocalDateTime createdAt,
			String vaBankCode) throws Exception {
		LocalDateTime effectiveCreatedAt = createdAt != null ? createdAt : LocalDateTime.now();
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into payment (order_id, toss_order_id, status, amount, approved_at, va_bank_code, "
								+ "created_at, updated_at) values (?, ?, ?, 30000, ?, ?, ?, ?)")) {
			statement.setLong(1, orderId);
			statement.setString(2, "toss-order-" + orderId);
			statement.setString(3, status);
			if (approvedAt == null) {
				statement.setNull(4, Types.TIMESTAMP);
			} else {
				statement.setTimestamp(4, Timestamp.valueOf(approvedAt));
			}
			statement.setString(5, vaBankCode);
			statement.setTimestamp(6, Timestamp.valueOf(effectiveCreatedAt));
			statement.setTimestamp(7, Timestamp.valueOf(effectiveCreatedAt));
			statement.executeUpdate();
		}
	}

	private long insertLimitedDrop(long productId) throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into limited_drop (total_quantity, close_at, open_at, product_id, status, "
								+ "created_at, updated_at) "
								+ "values (10, '2026-09-10 00:00:00', '2026-09-01 00:00:00', ?, 'CLOSED', "
								+ "now(6), now(6))",
						Statement.RETURN_GENERATED_KEYS)) {
			statement.setLong(1, productId);
			statement.executeUpdate();
			return generatedId(statement);
		}
	}

	private void insertLimitedPurchase(long dropId, long memberId, long orderId) throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into limited_purchase (drop_id, member_id, order_id, created_at, updated_at) "
								+ "values (?, ?, ?, now(6), now(6))")) {
			statement.setLong(1, dropId);
			statement.setLong(2, memberId);
			statement.setLong(3, orderId);
			statement.executeUpdate();
		}
	}

	private long generatedId(PreparedStatement statement) throws Exception {
		ResultSet keys = statement.getGeneratedKeys();
		keys.next();
		return keys.getLong(1);
	}
}
