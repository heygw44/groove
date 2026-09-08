package com.groove.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.catalog.client.PressingLookupClient;
import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.client.dto.DiscogsSearchResponse;
import com.groove.catalog.dto.CatalogLookupRequest;
import com.groove.catalog.dto.CatalogLookupResponse;
import com.groove.catalog.dto.CatalogReleaseDetailResponse;
import com.groove.fixture.DiscogsFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.global.common.PageResponse;
import com.groove.product.entity.Genre;
import com.groove.product.repository.GenreRepository;
import com.groove.product.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class CatalogLookupServiceTest {

	@Mock
	private PressingLookupClient client;

	@Mock
	private ProductRepository productRepository;

	@Mock
	private GenreRepository genreRepository;

	@Mock
	private DiscogsReleaseMapper mapper;

	@InjectMocks
	private CatalogLookupService catalogLookupService;

	@Nested
	@DisplayName("lookup()")
	class Lookup {

		@Test
		@DisplayName("검색 조건이 전부 없으면 COMMON_INVALID_INPUT 을 던진다")
		void throwsWhenNoCriteriaGiven() {
			// given
			CatalogLookupRequest request = new CatalogLookupRequest(null, null, "  ", null);

			// when & then
			assertThatThrownBy(() -> catalogLookupService.lookup(request))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.COMMON_INVALID_INPUT);
		}

		@Test
		@DisplayName("이미 등록된 discogsReleaseId 는 alreadyImported 를 true 로 매핑한다")
		void marksAlreadyImportedForExistingReleaseIds() {
			// given
			CatalogLookupRequest request = new CatalogLookupRequest(null, null, "nirvana", 0);
			DiscogsSearchResponse.Result result = DiscogsFixture.searchResult(7097051L, "Nirvana - Nevermind", "2015");
			DiscogsSearchResponse response = new DiscogsSearchResponse(
					new DiscogsSearchResponse.Pagination(1, 1, 20, 1), List.of(result));
			CatalogLookupResponse mapped = new CatalogLookupResponse(7097051L, "Nevermind", "Nirvana", 2015, "US",
					"CS 8163", "DGC", "https://thumb", true);
			given(client.search(null, null, "nirvana", 0)).willReturn(response);
			given(productRepository.findExistingDiscogsReleaseIds(anyList())).willReturn(List.of(7097051L));
			given(mapper.toLookup(result, true)).willReturn(mapped);

			// when
			PageResponse<CatalogLookupResponse> page = catalogLookupService.lookup(request);

			// then
			assertThat(page.content()).containsExactly(mapped);
			assertThat(page.totalElements()).isEqualTo(1);
			assertThat(page.page()).isEqualTo(0);
		}

		@Test
		@DisplayName("page 는 0-base 로 그대로 클라이언트에 전달된다")
		void passesZeroBasedPageToClient() {
			// given
			CatalogLookupRequest request = new CatalogLookupRequest("5012394144777", null, null, 2);
			DiscogsSearchResponse response = new DiscogsSearchResponse(
					new DiscogsSearchResponse.Pagination(3, 5, 20, 0), List.of());
			given(client.search("5012394144777", null, null, 2)).willReturn(response);

			// when
			catalogLookupService.lookup(request);

			// then
			verify(client).search("5012394144777", null, null, 2);
		}

		@Test
		@DisplayName("results 가 null 이면 빈 목록으로 매핑한다")
		void returnsEmptyContentWhenResultsIsNull() {
			// given
			CatalogLookupRequest request = new CatalogLookupRequest(null, null, "nirvana", 0);
			DiscogsSearchResponse response = new DiscogsSearchResponse(
					new DiscogsSearchResponse.Pagination(1, 1, 20, 0), null);
			given(client.search(null, null, "nirvana", 0)).willReturn(response);

			// when
			PageResponse<CatalogLookupResponse> page = catalogLookupService.lookup(request);

			// then
			assertThat(page.content()).isEmpty();
		}

		@Test
		@DisplayName("pagination 이 null 이면 결과 개수를 총 개수로 사용한다")
		void usesContentSizeAsTotalWhenPaginationIsNull() {
			// given
			CatalogLookupRequest request = new CatalogLookupRequest(null, null, "nirvana", 0);
			DiscogsSearchResponse.Result result = DiscogsFixture.searchResult(7097051L, "Nirvana - Nevermind", "2015");
			DiscogsSearchResponse response = new DiscogsSearchResponse(null, List.of(result));
			CatalogLookupResponse mapped = new CatalogLookupResponse(7097051L, "Nevermind", "Nirvana", 2015, "US",
					"CS 8163", "DGC", "https://thumb", false);
			given(client.search(null, null, "nirvana", 0)).willReturn(response);
			given(productRepository.findExistingDiscogsReleaseIds(anyList())).willReturn(List.of());
			given(mapper.toLookup(result, false)).willReturn(mapped);

			// when
			PageResponse<CatalogLookupResponse> page = catalogLookupService.lookup(request);

			// then
			assertThat(page.totalElements()).isEqualTo(1);
		}
	}

	@Nested
	@DisplayName("getRelease()")
	class GetRelease {

		@Test
		@DisplayName("장르 저장소에서 알려진 장르명을 조회해 매퍼에 전달한다")
		void delegatesKnownGenreNamesToMapper() {
			// given
			DiscogsReleaseResponse release = DiscogsFixture.releaseResponse("Miles Davis", "Columbia", "CS 8163",
					List.of("LP"), null, List.of("Jazz"), List.of());
			CatalogReleaseDetailResponse detail = new CatalogReleaseDetailResponse(249504L, 21247L, "Kind Of Blue",
					"Miles Davis", "Columbia", "US", 1959, "CS 8163", null, null, List.of("Jazz"), "https://image",
					"설명");
			given(client.getRelease(249504L)).willReturn(release);
			given(genreRepository.findAllByOrderByNameAsc()).willReturn(List.of(genreOf("Jazz"), genreOf("Rock")));
			given(mapper.toDetail(eq(release), any())).willReturn(detail);

			// when
			CatalogReleaseDetailResponse result = catalogLookupService.getRelease(249504L);

			// then
			assertThat(result).isEqualTo(detail);
		}
	}

	private Genre genreOf(String name) {
		Genre genre = Genre.create(name);
		return genre;
	}
}
