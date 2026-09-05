package com.groove.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import com.groove.fixture.GenreFixture;
import com.groove.product.entity.Genre;
import com.groove.support.DataJpaTestSupport;

class GenreRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private GenreRepository genreRepository;

	@Nested
	@DisplayName("findByName()")
	class FindByName {

		@Test
		@DisplayName("존재하는 이름이면 장르를 반환한다")
		void returnsGenreWhenExists() {
			// given
			Genre genre = genreRepository.save(GenreFixture.create("Jazz-findByName-exists"));

			// when
			Optional<Genre> found = genreRepository.findByName("Jazz-findByName-exists");

			// then
			assertThat(found).isPresent();
			assertThat(found.get().getId()).isEqualTo(genre.getId());
		}

		@Test
		@DisplayName("존재하지 않는 이름이면 empty 를 반환한다")
		void returnsEmptyWhenNotExists() {
			// when
			Optional<Genre> found = genreRepository.findByName("Jazz-findByName-none");

			// then
			assertThat(found).isEmpty();
		}
	}

	@Nested
	@DisplayName("existsByName()")
	class ExistsByName {

		@Test
		@DisplayName("존재하는 이름이면 true 를 반환한다")
		void returnsTrueWhenExists() {
			// given
			genreRepository.save(GenreFixture.create("Jazz-existsByName-exists"));

			// when & then
			assertThat(genreRepository.existsByName("Jazz-existsByName-exists")).isTrue();
		}

		@Test
		@DisplayName("존재하지 않는 이름이면 false 를 반환한다")
		void returnsFalseWhenNotExists() {
			// when & then
			assertThat(genreRepository.existsByName("Jazz-existsByName-none")).isFalse();
		}
	}

	@Nested
	@DisplayName("save()")
	class Save {

		@Test
		@DisplayName("같은 이름을 두 번 저장하면 DataIntegrityViolationException 이 발생한다")
		void throwsWhenNameDuplicated() {
			// given
			genreRepository.saveAndFlush(GenreFixture.create("Jazz-save-duplicate"));

			// when & then
			assertThatThrownBy(() -> genreRepository.saveAndFlush(GenreFixture.create("Jazz-save-duplicate")))
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}
}
