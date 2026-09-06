package com.groove.catalog.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.client.dto.DiscogsSearchResponse;
import com.groove.fixture.DiscogsFixture;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;

@ExtendWith(MockitoExtension.class)
class DiscogsClientTest {

	private static final String BASE_URL = "https://api.discogs.com";
	private static final String TOKEN = "test_token_dummy";
	private static final String USER_AGENT = "GrooveLP/1.0 +https://groove-lp.duckdns.org";

	@Mock
	private DiscogsRateLimiter rateLimiter;

	private MockRestServiceServer server;
	private DiscogsClient discogsClient;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
				.baseUrl(BASE_URL)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Discogs token=" + TOKEN)
				.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT);
		server = MockRestServiceServer.bindTo(builder).build();
		discogsClient = new DiscogsClient(builder.build(), rateLimiter);
	}

	@AfterEach
	void tearDown() {
		server.verify();
	}

	@Nested
	@DisplayName("search()")
	class Search {

		@Test
		@DisplayName("인증 헤더와 검색 조건을 붙여 요청하고 응답 헤더로 리미터를 갱신한다")
		void sendsAuthHeadersAndUpdatesRateLimiterFromResponseHeader() {
			// given
			server.expect(requestTo(Matchers.startsWith(BASE_URL + "/database/search")))
					.andExpect(method(HttpMethod.GET))
					.andExpect(header(HttpHeaders.AUTHORIZATION, "Discogs token=" + TOKEN))
					.andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
					.andExpect(queryParam("type", "release"))
					.andExpect(queryParam("page", "1"))
					.andExpect(queryParam("per_page", "20"))
					.andExpect(queryParam("q", "nirvana%20nevermind"))
					.andRespond(withSuccess(DiscogsFixture.SEARCH_RESPONSE_JSON, MediaType.APPLICATION_JSON)
							.header("X-Discogs-Ratelimit-Remaining", "59"));

			// when
			DiscogsSearchResponse response = discogsClient.search(null, null, "nirvana nevermind", 0);

			// then
			assertThat(response.results()).hasSize(1);
			assertThat(response.results().get(0).id()).isEqualTo(7097051L);
			verify(rateLimiter).acquire();
			verify(rateLimiter).updateRemaining(59);
			verifyNoMoreInteractions(rateLimiter);
		}

		@Test
		@DisplayName("404 응답이면 CATALOG_LOOKUP_FAILED 로 변환한다")
		void translatesNotFoundToLookupFailed() {
			// given
			server.expect(requestTo(Matchers.startsWith(BASE_URL + "/database/search")))
					.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
							.body(DiscogsFixture.NOT_FOUND_RESPONSE_JSON));

			// when & then
			assertThatThrownBy(() -> discogsClient.search("123", null, null, 0))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CATALOG_LOOKUP_FAILED);
		}

		@Test
		@DisplayName("429 응답이면 Retry-After 만큼 리미터를 블록하고 CATALOG_RATE_LIMITED 를 던진다")
		void translatesTooManyRequestsToRateLimited() {
			// given
			server.expect(requestTo(Matchers.startsWith(BASE_URL + "/database/search")))
					.andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "3"));

			// when & then
			assertThatThrownBy(() -> discogsClient.search(null, "CS-8163", null, 0))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CATALOG_RATE_LIMITED);
			verify(rateLimiter).blockFor(3);
		}

		@Test
		@DisplayName("500 응답이면 CATALOG_LOOKUP_FAILED 로 변환한다")
		void translatesServerErrorToLookupFailed() {
			// given
			server.expect(requestTo(Matchers.startsWith(BASE_URL + "/database/search")))
					.andRespond(withServerError());

			// when & then
			assertThatThrownBy(() -> discogsClient.search(null, null, "nirvana", 0))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CATALOG_LOOKUP_FAILED);
		}
	}

	@Nested
	@DisplayName("getRelease()")
	class GetRelease {

		@Test
		@DisplayName("정상 응답이면 릴리즈 정보를 그대로 반환한다")
		void returnsReleaseResponseOnSuccess() {
			// given
			server.expect(requestTo(BASE_URL + "/releases/249504"))
					.andExpect(method(HttpMethod.GET))
					.andRespond(withSuccess(DiscogsFixture.RELEASE_RESPONSE_JSON, MediaType.APPLICATION_JSON));

			// when
			DiscogsReleaseResponse response = discogsClient.getRelease(249504L);

			// then
			assertThat(response.id()).isEqualTo(249504L);
			assertThat(response.title()).isEqualTo("Kind Of Blue");
		}

		@Test
		@DisplayName("404 응답이면 CATALOG_RELEASE_NOT_FOUND 로 변환한다")
		void translatesNotFoundToReleaseNotFound() {
			// given
			server.expect(requestTo(BASE_URL + "/releases/999"))
					.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
							.body(DiscogsFixture.NOT_FOUND_RESPONSE_JSON));

			// when & then
			assertThatThrownBy(() -> discogsClient.getRelease(999L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CATALOG_RELEASE_NOT_FOUND);
		}
	}
}
