package com.groove.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
import com.groove.product.dto.ArtistResponse;
import com.groove.product.dto.ProductSuggestionResponse;
import com.groove.product.entity.Artist;
import com.groove.product.mapper.ProductSearchMapper;
import com.groove.product.repository.ArtistRepository;

@ExtendWith(MockitoExtension.class)
class ProductSuggestServiceTest {

	@Mock
	private ProductSearchMapper productSearchMapper;

	@Mock
	private ArtistRepository artistRepository;

	private ProductSuggestService productSuggestService;

	@BeforeEach
	void setUp() {
		productSuggestService = new ProductSuggestService(productSearchMapper, artistRepository);
	}

	@Nested
	@DisplayName("suggest()")
	class Suggest {

		@Test
		@DisplayName("keyword 의 앞뒤 공백을 제거하고 조회한다")
		void trimsKeyword() {
			// given
			given(productSearchMapper.suggestProducts(any(), anyInt())).willReturn(List.of());
			given(artistRepository.searchByKeyword(any(), any())).willReturn(List.of());
			ArgumentCaptor<String> mapperKeywordCaptor = ArgumentCaptor.forClass(String.class);
			ArgumentCaptor<String> artistKeywordCaptor = ArgumentCaptor.forClass(String.class);

			// when
			productSuggestService.suggest("  kind  ");

			// then
			verify(productSearchMapper).suggestProducts(mapperKeywordCaptor.capture(), anyInt());
			verify(artistRepository).searchByKeyword(artistKeywordCaptor.capture(), any());
			assertThat(mapperKeywordCaptor.getValue()).isEqualTo("kind");
			assertThat(artistKeywordCaptor.getValue()).isEqualTo("kind");
		}

		@Test
		@DisplayName("상품은 최대 5건까지 조회한다")
		void limitsProductsToFive() {
			// given
			given(productSearchMapper.suggestProducts(any(), anyInt())).willReturn(List.of());
			given(artistRepository.searchByKeyword(any(), any())).willReturn(List.of());

			// when
			productSuggestService.suggest("kind");

			// then
			verify(productSearchMapper).suggestProducts(eq("kind"), eq(5));
		}

		@Test
		@DisplayName("아티스트는 최대 3건까지 조회한다")
		void limitsArtistsToThree() {
			// given
			given(productSearchMapper.suggestProducts(any(), anyInt())).willReturn(List.of());
			given(artistRepository.searchByKeyword(any(), any())).willReturn(List.of());
			ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

			// when
			productSuggestService.suggest("kind");

			// then
			verify(artistRepository).searchByKeyword(eq("kind"), pageableCaptor.capture());
			assertThat(pageableCaptor.getValue()).isEqualTo(PageRequest.of(0, 3));
		}

		@Test
		@DisplayName("상품·아티스트 조회 결과를 응답으로 조합한다")
		void combinesProductsAndArtistsIntoResponse() {
			// given
			ProductSuggestionResponse.Item item = new ProductSuggestionResponse.Item(1L, "Kind of Blue",
					"Miles Davis", "https://cdn.groove.com/kind-of-blue.jpg");
			Artist artist = ArtistFixture.withId(1L);
			given(productSearchMapper.suggestProducts(any(), anyInt())).willReturn(List.of(item));
			given(artistRepository.searchByKeyword(any(), any())).willReturn(List.of(artist));

			// when
			ProductSuggestionResponse result = productSuggestService.suggest("kind");

			// then
			assertThat(result.products()).containsExactly(item);
			assertThat(result.artists()).extracting(ArtistResponse::id).containsExactly(artist.getId());
		}
	}
}
