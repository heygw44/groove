package com.groove.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import com.groove.product.entity.Artist;
import com.groove.support.DataJpaTestSupport;

import jakarta.persistence.EntityManager;

class ArtistRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private EntityManager entityManager;

	@Nested
	@DisplayName("searchByKeyword()")
	class SearchByKeyword {

		@Test
		@DisplayName("name 에 대소문자 구분 없이 부분일치하면 조회된다")
		void matchesNameCaseInsensitively() {
			// given
			Artist artist = artistRepository.save(Artist.create("Miles Davis-ART-1", "Miles Davis EN-ART-1", null));
			flushAndClear();

			// when
			List<Artist> found = artistRepository.searchByKeyword("miles davis-art-1", PageRequest.of(0, 20));

			// then
			assertThat(found).extracting(Artist::getId).contains(artist.getId());
		}

		@Test
		@DisplayName("nameEn 에 부분일치하면 조회된다")
		void matchesNameEn() {
			// given
			Artist artist = artistRepository.save(Artist.create("마일스 데이비스-ART-2", "Miles Davis-ART-2", null));
			flushAndClear();

			// when
			List<Artist> found = artistRepository.searchByKeyword("davis-art-2", PageRequest.of(0, 20));

			// then
			assertThat(found).extracting(Artist::getId).contains(artist.getId());
		}

		@Test
		@DisplayName("keyword 가 null 이면 전체 아티스트를 이름순으로 조회한다")
		void returnsAllWhenKeywordNull() {
			// given
			Artist artist = artistRepository.save(Artist.create("Bill Evans-ART-3", "Bill Evans EN-ART-3", null));
			flushAndClear();

			// when
			List<Artist> found = artistRepository.searchByKeyword(null, PageRequest.of(0, 100));

			// then
			assertThat(found).extracting(Artist::getId).contains(artist.getId());
		}

		@Test
		@DisplayName("Pageable 크기를 넘지 않는다")
		void respectsPageableLimit() {
			// given
			artistRepository.save(Artist.create("Coltrane-ART-4-A", "Coltrane EN A", null));
			artistRepository.save(Artist.create("Coltrane-ART-4-B", "Coltrane EN B", null));
			flushAndClear();

			// when
			List<Artist> found = artistRepository.searchByKeyword("Coltrane-ART-4", PageRequest.of(0, 1));

			// then
			assertThat(found).hasSize(1);
		}
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}
}
