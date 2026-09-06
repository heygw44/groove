package com.groove.global.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V4 마이그레이션이 기존(V3 까지) product 데이터를 album 으로 백필하는지 검증한다.
 * Spring 컨텍스트 없이 Flyway API 로 V1~V3 만 적용한 뒤 레거시 데이터를 심고, V4 까지 마저 적용해 결과를 확인한다.
 * 컨테이너를 테스트마다 새로 띄워 V4 가 이미 적용된 스키마가 다음 테스트로 새는 것을 막는다.
 */
class AlbumBackfillMigrationTest {

	private final MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
			.withDatabaseName("groove_album_backfill")
			.withUsername("groove")
			.withPassword("groove1234")
			.withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

	@AfterEach
	void stopContainer() {
		mysql.stop();
	}

	@BeforeEach
	void migrateUpToV3() {
		mysql.start();
		Flyway.configure()
				.dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
				.target("3")
				.load()
				.migrate();
	}

	private Connection connect() throws Exception {
		return DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
	}

	@Nested
	@DisplayName("migrate() V4")
	class MigrateV4 {

		@Test
		@DisplayName("같은 (title, artist_id) 상품 여러 건을 앨범 하나로 묶고 album_id 를 연결한다")
		void backfillsAlbumAndLinksExistingProducts() throws Exception {
			// given
			long artistId = insertArtist("Miles Davis");
			long productId1 = insertLegacyProduct("Kind of Blue", artistId, "1959-08-17");
			long productId2 = insertLegacyProduct("Kind of Blue", artistId, "1959-08-17");

			// when
			Flyway.configure()
					.dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
					.load()
					.migrate();

			// then
			try (Connection connection = connect()) {
				try (Statement statement = connection.createStatement()) {
					ResultSet albumCountResult = statement.executeQuery(
							"select count(*) from album where title = 'Kind of Blue' and artist_id = " + artistId);
					albumCountResult.next();
					assertThat(albumCountResult.getInt(1)).isEqualTo(1);
				}

				Long albumId = findAlbumId(connection, artistId);
				assertThat(albumId).isNotNull();
				assertThat(findProductAlbumId(connection, productId1)).isEqualTo(albumId);
				assertThat(findProductAlbumId(connection, productId2)).isEqualTo(albumId);
			}
		}

		@Test
		@DisplayName("마이그레이션 이후 product.album_id 는 NOT NULL 이다")
		void makesAlbumIdNotNullAfterMigration() throws Exception {
			// given
			long artistId = insertArtist("John Coltrane");
			insertLegacyProduct("A Love Supreme", artistId, "1965-01-01");

			// when
			Flyway.configure()
					.dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
					.load()
					.migrate();

			// then
			try (Connection connection = connect()) {
				try (Statement statement = connection.createStatement()) {
					ResultSet result = statement.executeQuery(
							"select is_nullable from information_schema.columns "
									+ "where table_schema = database() and table_name = 'product' "
									+ "and column_name = 'album_id'");
					result.next();
					assertThat(result.getString(1)).isEqualTo("NO");
				}
			}
		}
	}

	private Long findAlbumId(Connection connection, long artistId) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
				"select id from album where artist_id = ?")) {
			statement.setLong(1, artistId);
			ResultSet result = statement.executeQuery();
			result.next();
			return result.getLong(1);
		}
	}

	private Long findProductAlbumId(Connection connection, long productId) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement(
				"select album_id from product where id = ?")) {
			statement.setLong(1, productId);
			ResultSet result = statement.executeQuery();
			result.next();
			return result.getLong(1);
		}
	}

	private long insertArtist(String name) throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into artist (name, created_at, updated_at) values (?, now(6), now(6))",
						Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, name);
			statement.executeUpdate();
			ResultSet keys = statement.getGeneratedKeys();
			keys.next();
			return keys.getLong(1);
		}
	}

	private long insertLegacyProduct(String title, long artistId, String releaseDate) throws Exception {
		try (Connection connection = connect();
				PreparedStatement statement = connection.prepareStatement(
						"insert into product (title, artist_id, release_date, price, status, review_count, "
								+ "created_at, updated_at) values (?, ?, ?, 30000, 'ON_SALE', 0, now(6), now(6))",
						Statement.RETURN_GENERATED_KEYS)) {
			statement.setString(1, title);
			statement.setLong(2, artistId);
			statement.setString(3, releaseDate);
			statement.executeUpdate();
			ResultSet keys = statement.getGeneratedKeys();
			keys.next();
			return keys.getLong(1);
		}
	}
}
