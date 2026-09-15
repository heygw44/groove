package com.groove.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.groove.auth.dto.AuthTokens;
import com.groove.auth.dto.LoginRequest;
import com.groove.auth.dto.SignupRequest;
import com.groove.auth.jwt.JwtProvider;
import com.groove.auth.repository.RefreshTokenRepository;
import com.groove.auth.service.AuthService;
import com.groove.global.common.BusinessException;
import com.groove.global.common.ErrorCode;
import com.groove.support.IntegrationTestSupport;

class RefreshTokenRotationIntegrationTest extends IntegrationTestSupport {

	private static final int CONCURRENT_REQUEST_COUNT = 20;
	private static final String PASSWORD = "password1";

	@Autowired
	AuthService authService;

	@Autowired
	RefreshTokenRepository refreshTokenRepository;

	@Autowired
	JwtProvider jwtProvider;

	@Autowired
	StringRedisTemplate redisTemplate;

	private ExecutorService executorService;

	@BeforeEach
	void setUp() {
		executorService = Executors.newFixedThreadPool(CONCURRENT_REQUEST_COUNT);
	}

	@AfterEach
	void tearDown() {
		executorService.shutdownNow();
	}

	@Nested
	@DisplayName("동시 reissue")
	class ConcurrentReissue {

		@Test
		@DisplayName("같은 토큰으로 20건이 동시에 들어오면 전부 성공하고 발급된 토큰은 1종류다")
		void allSucceedWithSingleDistinctToken() throws InterruptedException {
			// given
			String email = signup();
			AuthTokens loginTokens = login(email);
			String presentedToken = loginTokens.refreshToken();
			Long memberId = jwtProvider.parseAccessToken(loginTokens.accessToken()).memberId();
			String sessionId = jwtProvider.parseRefreshToken(presentedToken).sessionId();

			CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUEST_COUNT);
			CountDownLatch startLatch = new CountDownLatch(1);
			List<AtomicReference<AuthTokens>> results = new ArrayList<>();
			List<AtomicReference<Throwable>> failures = new ArrayList<>();

			// when
			for (int i = 0; i < CONCURRENT_REQUEST_COUNT; i++) {
				AtomicReference<AuthTokens> result = new AtomicReference<>();
				AtomicReference<Throwable> failure = new AtomicReference<>();
				results.add(result);
				failures.add(failure);
				executorService.submit(() -> {
					try {
						readyLatch.countDown();
						startLatch.await();
						result.set(authService.reissue(presentedToken));
					} catch (Throwable throwable) {
						failure.set(throwable);
					}
				});
			}
			readyLatch.await();
			startLatch.countDown();
			executorService.shutdown();
			boolean finished = executorService.awaitTermination(30, TimeUnit.SECONDS);

			// then
			assertThat(finished).isTrue();
			assertThat(failures).extracting(AtomicReference::get).allMatch(throwable -> throwable == null);
			List<String> distinctRefreshTokens = results.stream()
					.map(AtomicReference::get)
					.map(AuthTokens::refreshToken)
					.distinct()
					.collect(Collectors.toList());
			assertThat(distinctRefreshTokens).hasSize(1);
			assertThat(refreshTokenRepository.findCurrent(memberId, sessionId)).contains(distinctRefreshTokens.get(0));
		}
	}

	@Nested
	@DisplayName("grace 구간")
	class GracePeriod {

		@Test
		@DisplayName("회전 직후 grace 안에 옛 토큰으로 재발급하면 성공하고 현재 토큰을 그대로 돌려준다")
		void returnsCurrentTokenWithinGrace() {
			// given
			String email = signup();
			AuthTokens loginTokens = login(email);
			String firstToken = loginTokens.refreshToken();

			AuthTokens rotated = authService.reissue(firstToken);
			String currentToken = rotated.refreshToken();

			// when
			AuthTokens result = authService.reissue(firstToken);

			// then
			assertThat(result.refreshToken()).isEqualTo(currentToken);
		}

		@Test
		@DisplayName("grace 를 지난 옛 토큰으로 재발급하면 MISMATCH 이고 해당 세션만 폐기되며 다른 세션은 유지된다")
		void discardsOnlyThatSessionAfterGraceElapsed() {
			// given
			String email = signup();
			AuthTokens firstLoginTokens = login(email);
			AuthTokens secondLoginTokens = login(email);
			Long memberId = jwtProvider.parseAccessToken(firstLoginTokens.accessToken()).memberId();
			String firstSessionOldToken = firstLoginTokens.refreshToken();
			String firstSessionId = jwtProvider.parseRefreshToken(firstSessionOldToken).sessionId();
			String secondSessionId = jwtProvider.parseRefreshToken(secondLoginTokens.refreshToken()).sessionId();

			authService.reissue(firstSessionOldToken);
			expirePrevGrace(memberId, firstSessionId);

			// when & then
			assertThatThrownBy(() -> authService.reissue(firstSessionOldToken))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_MISMATCH);
			assertThat(refreshTokenRepository.findCurrent(memberId, firstSessionId)).isEmpty();

			// 두 번째 로그인 세션은 영향받지 않는다
			AuthTokens secondSessionReissued = authService.reissue(secondLoginTokens.refreshToken());
			assertThat(refreshTokenRepository.findCurrent(memberId, secondSessionId))
					.contains(secondSessionReissued.refreshToken());
		}
	}

	@Nested
	@DisplayName("기기별 세션 분리")
	class MultiDeviceSessions {

		@Test
		@DisplayName("두 기기가 번갈아 reissue 를 반복해도 서로 영향을 주지 않는다")
		void independentAcrossAlternatingReissues() {
			// given
			String email = signup();
			AuthTokens device1Tokens = login(email);
			AuthTokens device2Tokens = login(email);
			Long memberId = jwtProvider.parseAccessToken(device1Tokens.accessToken()).memberId();
			String device1Session = jwtProvider.parseRefreshToken(device1Tokens.refreshToken()).sessionId();
			String device2Session = jwtProvider.parseRefreshToken(device2Tokens.refreshToken()).sessionId();

			// when
			String device1Current = device1Tokens.refreshToken();
			String device2Current = device2Tokens.refreshToken();
			for (int i = 0; i < 5; i++) {
				device1Current = authService.reissue(device1Current).refreshToken();
				device2Current = authService.reissue(device2Current).refreshToken();
			}

			// then
			assertThat(refreshTokenRepository.findCurrent(memberId, device1Session)).contains(device1Current);
			assertThat(refreshTokenRepository.findCurrent(memberId, device2Session)).contains(device2Current);
			assertThat(device1Current).isNotEqualTo(device2Current);
		}
	}

	@Nested
	@DisplayName("회원 정지")
	class MemberSuspension {

		@Test
		@DisplayName("모든 세션이 폐기되어 어떤 세션으로도 reissue 가 NOT_FOUND 다")
		void allSessionsFailAfterSuspension() {
			// given
			String email = signup();
			AuthTokens device1Tokens = login(email);
			AuthTokens device2Tokens = login(email);
			Long memberId = jwtProvider.parseAccessToken(device1Tokens.accessToken()).memberId();

			// when
			// AdminMemberService.changeStatus(SUSPENDED) 가 호출하는 것과 같은 전체 세션 폐기 경로다.
			refreshTokenRepository.deleteAllByMemberId(memberId);

			// then
			assertThatThrownBy(() -> authService.reissue(device1Tokens.refreshToken()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND);
			assertThatThrownBy(() -> authService.reissue(device2Tokens.refreshToken()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND);
		}
	}

	@Nested
	@DisplayName("로그아웃")
	class Logout {

		@Test
		@DisplayName("로그아웃한 세션만 지우고 다른 세션은 유지한다")
		void deletesOnlyLoggedOutSession() {
			// given
			String email = signup();
			AuthTokens device1Tokens = login(email);
			AuthTokens device2Tokens = login(email);
			Long memberId = jwtProvider.parseAccessToken(device1Tokens.accessToken()).memberId();
			String device2Session = jwtProvider.parseRefreshToken(device2Tokens.refreshToken()).sessionId();

			// when
			authService.logout(memberId, device1Tokens.refreshToken());

			// then
			assertThatThrownBy(() -> authService.reissue(device1Tokens.refreshToken()))
					.isInstanceOf(BusinessException.class)
					.extracting("errorCode")
					.isEqualTo(ErrorCode.AUTH_REFRESH_TOKEN_NOT_FOUND);
			assertThat(refreshTokenRepository.findCurrent(memberId, device2Session))
					.contains(device2Tokens.refreshToken());
		}
	}

	private String signup() {
		String email = "rotation-" + UUID.randomUUID() + "@groove.com";
		authService.signup(new SignupRequest(email, PASSWORD, "그루버"));
		return email;
	}

	private AuthTokens login(String email) {
		return authService.login(new LoginRequest(email, PASSWORD));
	}

	private void expirePrevGrace(Long memberId, String sessionId) {
		redisTemplate.opsForHash().put("refresh:" + memberId + ":" + sessionId, "prev_exp", "0");
	}
}
