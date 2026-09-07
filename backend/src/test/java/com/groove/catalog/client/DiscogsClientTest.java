package com.groove.catalog.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketException;
import java.time.Duration;

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
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.groove.catalog.client.dto.DiscogsReleaseResponse;
import com.groove.catalog.client.dto.DiscogsSearchResponse;
import com.groove.catalog.config.DiscogsProperties;
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
	private RecordingSleeper sleeper;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder()
				.baseUrl(BASE_URL)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Discogs token=" + TOKEN)
				.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT);
		server = MockRestServiceServer.bindTo(builder).build();
		sleeper = new RecordingSleeper();
		discogsClient = new DiscogsClient(builder.build(), rateLimiter, sleeper,
				new DiscogsProperties(BASE_URL, TOKEN, USER_AGENT, Duration.ofSeconds(5),
						new DiscogsProperties.Retry(2, Duration.ofMillis(50))));
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
			assertThat(sleeper.sleptDurations()).isEmpty();
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
			assertThat(sleeper.sleptDurations()).isEmpty();
		}

		@Test
		@DisplayName("500 응답이면 재시도한 뒤 CATALOG_LOOKUP_FAILED 로 변환한다")
		void retriesServerErrorThenTranslatesToLookupFailed() {
			// given
			server.expect(ExpectedCount.times(2), requestTo(Matchers.startsWith(BASE_URL + "/database/search")))
					.andRespond(withServerError());

			// when & then
			assertThatThrownBy(() -> discogsClient.search(null, null, "nirvana", 0))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CATALOG_LOOKUP_FAILED);
			verify(rateLimiter, times(2)).acquire();
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
			assertThat(sleeper.sleptDurations()).isEmpty();
		}

		@Test
		@DisplayName("연결이 끊기면 한 번 재시도해서 성공한다")
		void retriesOnceOnConnectionResetThenSucceeds() {
			// given
			server.expect(requestTo(BASE_URL + "/releases/249504"))
					.andRespond(withException(new SocketException("Connection reset")));
			server.expect(requestTo(BASE_URL + "/releases/249504"))
					.andRespond(withSuccess(DiscogsFixture.RELEASE_RESPONSE_JSON, MediaType.APPLICATION_JSON));

			// when
			DiscogsReleaseResponse response = discogsClient.getRelease(249504L);

			// then
			assertThat(response.id()).isEqualTo(249504L);
			verify(rateLimiter, times(2)).acquire();
			assertThat(sleeper.sleptDurations()).containsExactly(Duration.ofMillis(50));
		}

		@Test
		@DisplayName("재시도 한도를 소진하면 CATALOG_LOOKUP_FAILED 를 던진다")
		void throwsLookupFailedWhenRetriesExhausted() {
			// given
			server.expect(ExpectedCount.times(2), requestTo(BASE_URL + "/releases/249504"))
					.andRespond(withException(new SocketException("Connection reset")));

			// when & then
			assertThatThrownBy(() -> discogsClient.getRelease(249504L))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.CATALOG_LOOKUP_FAILED);
			verify(rateLimiter, times(2)).acquire();
			assertThat(sleeper.sleptDurations()).hasSize(1);
		}
	}
}
