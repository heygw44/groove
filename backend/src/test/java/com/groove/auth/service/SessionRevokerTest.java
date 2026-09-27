package com.groove.auth.service;

import static org.mockito.Mockito.inOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.groove.auth.repository.AccessTokenRevocationRepository;
import com.groove.auth.repository.RefreshTokenRepository;

@ExtendWith(MockitoExtension.class)
class SessionRevokerTest {

	private static final Long MEMBER_ID = 1L;

	@Mock
	RefreshTokenRepository refreshTokenRepository;

	@Mock
	AccessTokenRevocationRepository accessTokenRevocationRepository;

	Clock clock;

	SessionRevoker sessionRevoker;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(Instant.parse("2026-09-27T03:00:00Z"), ZoneId.of("Asia/Seoul"));
		sessionRevoker = new SessionRevoker(refreshTokenRepository, accessTokenRevocationRepository, clock);
	}

	@Nested
	@DisplayName("revokeAll()")
	class RevokeAll {

		@Test
		@DisplayName("refresh token 을 먼저 삭제하고 access token 을 클록의 시각으로 폐기한다")
		void deletesSessionsThenMarksAccessTokenRevoked() {
			// when
			sessionRevoker.revokeAll(MEMBER_ID);

			// then
			InOrder order = inOrder(refreshTokenRepository, accessTokenRevocationRepository);
			order.verify(refreshTokenRepository).deleteAllByMemberId(MEMBER_ID);
			order.verify(accessTokenRevocationRepository).markRevoked(MEMBER_ID, clock.instant().getEpochSecond());
		}
	}
}
