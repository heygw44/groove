package com.groove.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.groove.fixture.AlbumFixture;
import com.groove.fixture.ArtistFixture;
import com.groove.product.entity.Album;
import com.groove.product.entity.Artist;
import com.groove.support.DataJpaTestSupport;

import jakarta.persistence.EntityManager;

class AlbumRepositoryTest extends DataJpaTestSupport {

	@Autowired
	private AlbumRepository albumRepository;

	@Autowired
	private ArtistRepository artistRepository;

	@Autowired
	private EntityManager entityManager;

	@Nested
	@DisplayName("searchByKeyword()")
	class SearchByKeyword {

		@Test
		@DisplayName("keyword 가 없으면 전체 앨범이 조회된다")
		void returnsAllAlbumsWhenKeywordIsNull() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create("Miles Davis-ALB-1"));
			Album saved = albumRepository.save(AlbumFixture.create(artist, "Kind of Blue-ALB-1"));
			flushAndClear();

			// when
			Page<Album> page = albumRepository.searchByKeyword(null, PageRequest.of(0, 100));

			// then
			List<Long> ids = page.getContent().stream().map(Album::getId).toList();
			assertThat(ids).contains(saved.getId());
		}

		@Test
		@DisplayName("앨범 제목이 keyword 를 대소문자 무시하고 포함하면 조회된다")
		void matchesByTitleIgnoringCase() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create("John Coltrane-ALB-2"));
			Album saved = albumRepository.save(AlbumFixture.create(artist, "A Love Supreme-ALB-2"));
			flushAndClear();

			// when
			Page<Album> page = albumRepository.searchByKeyword("love supreme-alb-2", PageRequest.of(0, 100));

			// then
			assertThat(page.getContent()).extracting(Album::getId).contains(saved.getId());
		}

		@Test
		@DisplayName("아티스트명이 keyword 를 대소문자 무시하고 포함하면 조회된다")
		void matchesByArtistNameIgnoringCase() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create("Herbie Hancock-ALB-3"));
			Album saved = albumRepository.save(AlbumFixture.create(artist, "Head Hunters-ALB-3"));
			flushAndClear();

			// when
			Page<Album> page = albumRepository.searchByKeyword("herbie hancock-alb-3", PageRequest.of(0, 100));

			// then
			assertThat(page.getContent()).extracting(Album::getId).contains(saved.getId());
		}

		@Test
		@DisplayName("제목과 아티스트명 어디에도 없는 keyword 면 조회되지 않는다")
		void excludesAlbumWhenKeywordDoesNotMatch() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create("Bill Evans-ALB-4"));
			Album saved = albumRepository.save(AlbumFixture.create(artist, "Waltz for Debby-ALB-4"));
			flushAndClear();

			// when
			Page<Album> page = albumRepository.searchByKeyword("no-such-keyword-ALB-4", PageRequest.of(0, 100));

			// then
			assertThat(page.getContent()).extracting(Album::getId).doesNotContain(saved.getId());
		}

		@Test
		@DisplayName("artist 연관관계가 fetch join 으로 즉시 초기화된다")
		void fetchesArtistEagerly() {
			// given
			Artist artist = artistRepository.save(ArtistFixture.create("Chet Baker-ALB-5"));
			Album saved = albumRepository.save(AlbumFixture.create(artist, "Chet Baker Sings-ALB-5"));
			flushAndClear();

			// when
			Page<Album> page = albumRepository.searchByKeyword("Chet Baker Sings-ALB-5", PageRequest.of(0, 100));

			// then
			Album found = page.getContent().stream()
					.filter(album -> album.getId().equals(saved.getId()))
					.findFirst()
					.orElseThrow();
			assertThat(Hibernate.isInitialized(found.getArtist())).isTrue();
			assertThat(found.getArtist().getName()).isEqualTo("Chet Baker-ALB-5");
		}
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}
}
