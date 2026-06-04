package com.groove.auth.application;

import com.groove.auth.domain.RefreshToken;
import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.auth.domain.TokenHasher;
import com.groove.auth.security.JwtClaims;
import com.groove.auth.security.JwtProperties;
import com.groove.auth.security.JwtProvider;
import com.groove.common.exception.AuthException;
import com.groove.common.exception.ErrorCode;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Refresh Token 의 발급·회전·무효화·재사용 감지 (이슈 #22).
 *
 * <p>회전(rotate) 흐름:
 * <ol>
 *   <li>JWT 파싱으로 형식·서명·만료·typ 검증 → memberId 확보</li>
 *   <li>본문의 SHA-256 해시로 DB 행 조회 (모르는 토큰 → 401)</li>
 *   <li>이미 revoked 상태 → 재사용으로 간주, 같은 사용자 활성 토큰 전체 무효화</li>
 *   <li>DB 만료 검증 (JWT 검증과 별개의 방어선)</li>
 *   <li>새 access·refresh 발급 + 새 행 영속화</li>
 *   <li>{@link RefreshTokenRepository#revokeIfActive} 로 atomic CAS — 0 행이면 동시 회전
 *       경쟁에서 패배한 케이스. <b>전체 무효화 없이 단순 거부</b>한다 — 정상 사용자의
 *       동시 요청(네트워크 재시도/더블 클릭) 가능성이 있고, 실제 도난된 토큰은
 *       다음 사용 시 {@code isRevoked()=true} 로 잡혀 정상적으로 재사용 분기로 진입한다.</li>
 * </ol>
 *
 * <p>만료 검사 순서: revoked → DB expired 순으로 검사한다. 만료된 토큰의 재사용은
 * 재사용 분기에 들어가지 않고 단순 만료 응답만 반환한다 — 이미 expire 된 토큰은
 * 공격자가 새로 사용해도 access 발급으로 이어지지 않으므로 전체 세션 무효화까지
 * 가지 않는다.
 *
 * <p>{@code revoke} 는 RFC 7009 와 같이 토큰 유효성 노출을 차단하기 위해
 * 무효 입력에도 멱등 무동작으로 처리한다.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenAdmin refreshTokenAdmin;
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenAdmin refreshTokenAdmin,
            MemberRepository memberRepository,
            JwtProvider jwtProvider,
            JwtProperties jwtProperties,
            Clock clock
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenAdmin = refreshTokenAdmin;
        this.memberRepository = memberRepository;
        this.jwtProvider = jwtProvider;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    /**
     * 로그인 성공 시 access·refresh 토큰을 발급하고 refresh 행을 영속화한다.
     */
    @Transactional
    public TokenPair issueOnLogin(Member member) {
        Instant now = clock.instant();
        String access = jwtProvider.issueAccessToken(member.getId(), member.getRole());
        String refresh = jwtProvider.issueRefreshToken(member.getId());
        persistNewToken(member.getId(), refresh, now);
        return new TokenPair(access, refresh, jwtProperties.accessTokenTtlSeconds());
    }

    /**
     * Refresh Token Rotation. 새 access·refresh 를 발급하고 기존 refresh 는 즉시 revoke 한다.
     */
    @Transactional
    public TokenPair rotate(String rawRefreshToken) {
        JwtClaims claims = jwtProvider.parseRefreshToken(rawRefreshToken);
        Long memberId = claims.memberId();

        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> {
                    log.warn("리프레시 실패 - 알 수 없는 토큰 memberId={}", memberId);
                    return new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
                });

        Instant now = clock.instant();

        if (existing.isRevoked()) {
            log.warn("리프레시 토큰 재사용 감지 → 전체 세션 무효화 memberId={} tokenId={}",
                    memberId, existing.getId());
            refreshTokenAdmin.forceRevokeAllActiveSessions(memberId, now);
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        if (existing.isExpired(now)) {
            log.warn("리프레시 실패 - DB 만료 memberId={}", memberId);
            throw new AuthException(ErrorCode.AUTH_EXPIRED_TOKEN);
        }

        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(() -> {
                    log.warn("리프레시 실패 - 비활성 회원 memberId={}", memberId);
                    return new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
                });

        String newAccess = jwtProvider.issueAccessToken(member.getId(), member.getRole());
        String newRefresh = jwtProvider.issueRefreshToken(member.getId());
        RefreshToken newRow = persistNewToken(member.getId(), newRefresh, now);

        int affected = refreshTokenRepository.revokeIfActive(existing.getId(), now, newRow.getId());
        if (affected == 0) {
            // 동시 회전 race 패배 — 정상 사용자의 동시 요청일 가능성이 있으므로 전체 무효화는 하지 않는다.
            // 외부 트랜잭션 롤백으로 방금 INSERT 한 newRow 가 함께 취소되어 부작용 없음.
            log.warn("리프레시 회전 race 패배 - 단순 거부 memberId={} tokenId={}",
                    memberId, existing.getId());
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }

        log.info("리프레시 회전 성공 memberId={} oldId={} newId={}",
                memberId, existing.getId(), newRow.getId());
        return new TokenPair(newAccess, newRefresh, jwtProperties.accessTokenTtlSeconds());
    }

    /**
     * 로그아웃 단일 토큰 폐기. RFC 7009 § 2.2 — 토큰 유효성과 무관하게 멱등 동작한다.
     * 형식 오류·만료·미존재·이미 revoked 모두 조용히 무동작으로 끝낸다.
     */
    @Transactional
    public void revoke(String rawRefreshToken) {
        try {
            jwtProvider.parseRefreshToken(rawRefreshToken);
        } catch (AuthException e) {
            log.debug("로그아웃 - 잘못된 형식 또는 만료 토큰 (멱등 처리)");
            return;
        }
        String hash = TokenHasher.sha256Hex(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            if (!token.isRevoked()) {
                refreshTokenRepository.revokeIfActive(token.getId(), clock.instant(), null);
                log.info("로그아웃 - 토큰 폐기 tokenId={} memberId={}", token.getId(), token.getMemberId());
            }
        });
    }

    private RefreshToken persistNewToken(Long memberId, String rawRefreshToken, Instant issuedAt) {
        Instant expiresAt = issuedAt.plusSeconds(jwtProperties.refreshTokenTtlSeconds());
        RefreshToken row = RefreshToken.issue(
                memberId,
                TokenHasher.sha256Hex(rawRefreshToken),
                issuedAt,
                expiresAt
        );
        return refreshTokenRepository.save(row);
    }
}
