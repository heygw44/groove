package com.groove.global.init;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** {@link SeedCatalog}·{@link SeedAlbums} 가 V8 마이그레이션·완료 조건과 어긋나지 않는지 검증한다. */
class SeedCatalogTest {

	private static final int FIRST_NEW_GENRE_INDEX = 10;
	private static final int MIN_ALBUMS_PER_NEW_GENRE = 5;

	@Nested
	@DisplayName("GENRES")
	class Genres {

		@Test
		@DisplayName("V8 마이그레이션이 삽입하는 장르 집합과 같다")
		void matchesV8MigrationGenreSet() throws IOException {
			// given
			Set<String> migrationGenres = readV8GenreNames();

			// when & then
			assertThat(Set.copyOf(SeedCatalog.GENRES)).isEqualTo(migrationGenres);
		}

		private Set<String> readV8GenreNames() throws IOException {
			Pattern literal = Pattern.compile("select '([^']+)'");
			try (InputStream in = getClass().getResourceAsStream("/db/migration/V8__seed_genre_master.sql")) {
				assertThat(in).as("V8__seed_genre_master.sql 이 클래스패스에 있어야 한다").isNotNull();
				String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				Matcher matcher = literal.matcher(sql);
				Set<String> names = new LinkedHashSet<>();
				while (matcher.find()) {
					names.add(matcher.group(1));
				}
				return names;
			}
		}
	}

	@Nested
	@DisplayName("SeedAlbums.ALBUMS")
	class Albums {

		@Test
		@DisplayName("장르 25개 전부 최소 한 장 이상의 앨범과 매칭된다")
		void everyGenreHasAtLeastOneAlbum() {
			// given
			Set<Integer> usedGenreIndexes = SeedAlbums.ALBUMS.stream()
					.flatMap(album -> album.genreIndexes().stream())
					.collect(Collectors.toSet());

			// when & then
			List<Integer> allGenreIndexes = IntStream.range(0, SeedCatalog.GENRES.size()).boxed().toList();
			assertThat(usedGenreIndexes).containsExactlyInAnyOrderElementsOf(allGenreIndexes);
		}

		@Test
		@DisplayName("새로 추가한 장르 15개는 각각 최소 5장 이상의 앨범과 매칭된다")
		void newGenresHaveAtLeastFiveAlbums() {
			// given
			Map<Integer, Long> albumCountByGenreIndex = SeedAlbums.ALBUMS.stream()
					.flatMap(album -> album.genreIndexes().stream())
					.collect(Collectors.groupingBy(index -> index, Collectors.counting()));

			// when & then
			for (int genreIndex = FIRST_NEW_GENRE_INDEX; genreIndex < SeedCatalog.GENRES.size(); genreIndex++) {
				assertThat(albumCountByGenreIndex.getOrDefault(genreIndex, 0L))
						.as("장르 %s(인덱스 %d)", SeedCatalog.GENRES.get(genreIndex), genreIndex)
						.isGreaterThanOrEqualTo(MIN_ALBUMS_PER_NEW_GENRE);
			}
		}
	}
}
