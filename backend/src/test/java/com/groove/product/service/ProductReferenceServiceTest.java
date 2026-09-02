package com.groove.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.groove.fixture.ArtistFixture;
import com.groove.fixture.GenreFixture;
import com.groove.fixture.LabelFixture;
import com.groove.product.dto.ArtistResponse;
import com.groove.product.dto.GenreResponse;
import com.groove.product.dto.LabelResponse;
import com.groove.product.entity.Artist;
import com.groove.product.entity.Genre;
import com.groove.product.entity.Label;
import com.groove.product.repository.ArtistRepository;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.LabelRepository;

@ExtendWith(MockitoExtension.class)
class ProductReferenceServiceTest {

	@Mock
	private GenreRepository genreRepository;

	@Mock
	private LabelRepository labelRepository;

	@Mock
	private ArtistRepository artistRepository;

	private ProductReferenceService productReferenceService;

	@BeforeEach
	void setUp() {
		productReferenceService = new ProductReferenceService(genreRepository, labelRepository, artistRepository);
	}

	@Nested
	@DisplayName("getGenres()")
	class GetGenres {

		@Test
		@DisplayName("이름순 장르 목록을 응답으로 변환한다")
		void returnsGenresOrderedByName() {
			// given
			Genre jazz = GenreFixture.create("Jazz");
			given(genreRepository.findAllByOrderByNameAsc()).willReturn(List.of(jazz));

			// when
			List<GenreResponse> result = productReferenceService.getGenres();

			// then
			assertThat(result).extracting(GenreResponse::name).containsExactly("Jazz");
		}
	}

	@Nested
	@DisplayName("getLabels()")
	class GetLabels {

		@Test
		@DisplayName("이름순 레이블 목록을 응답으로 변환한다")
		void returnsLabelsOrderedByName() {
			// given
			Label label = LabelFixture.create();
			given(labelRepository.findAllByOrderByNameAsc()).willReturn(List.of(label));

			// when
			List<LabelResponse> result = productReferenceService.getLabels();

			// then
			assertThat(result).extracting(LabelResponse::name).containsExactly(label.getName());
		}
	}

	@Nested
	@DisplayName("searchArtists()")
	class SearchArtists {

		@Test
		@DisplayName("keyword 가 공백이면 null 키워드와 크기 20 페이지로 조회한다")
		void searchesWithNullKeywordWhenBlank() {
			// given
			given(artistRepository.searchByKeyword(isNull(), any())).willReturn(List.of());
			ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

			// when
			productReferenceService.searchArtists("  ");

			// then
			verify(artistRepository).searchByKeyword(isNull(), pageableCaptor.capture());
			assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(0, 20));
		}

		@Test
		@DisplayName("keyword 의 앞뒤 공백을 제거하고 조회한다")
		void trimsKeyword() {
			// given
			given(artistRepository.searchByKeyword(any(), any())).willReturn(List.of());
			ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);

			// when
			productReferenceService.searchArtists("  miles  ");

			// then
			verify(artistRepository).searchByKeyword(keywordCaptor.capture(), any());
			assertThat(keywordCaptor.getValue()).isEqualTo("miles");
		}

		@Test
		@DisplayName("아티스트 목록을 응답으로 변환한다")
		void mapsArtistsToResponse() {
			// given
			Artist artist = ArtistFixture.create("Miles Davis");
			given(artistRepository.searchByKeyword(any(), any())).willReturn(List.of(artist));

			// when
			List<ArtistResponse> result = productReferenceService.searchArtists(null);

			// then
			assertThat(result).extracting(ArtistResponse::name).containsExactly("Miles Davis");
		}
	}
}
