package com.groove.auth.application;

import com.groove.common.exception.AuthException;
import com.groove.common.exception.ErrorCode;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.exception.MemberNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 인증 흐름 애플리케이션 서비스. 로그인은 활성 회원 조회 → 비밀번호 BCrypt 비교 → 토큰 발급 순.
 * 이메일 미존재와 비밀번호 불일치는 모두 AUTH_INVALID_CREDENTIALS 로 응답한다(계정 열거 차단).
 * 토큰 발급·영속화는 RefreshTokenService 에 위임한다.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenAdmin refreshTokenAdmin;
    private final Clock clock;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            RefreshTokenAdmin refreshTokenAdmin,
            Clock clock
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenAdmin = refreshTokenAdmin;
        this.clock = clock;
    }

    public TokenPair login(LoginCommand command) {
        Member member = memberRepository.findByEmailAndDeletedAtIsNull(command.email())
                .orElseThrow(() -> {
                    log.warn("로그인 실패 - 이메일 미존재 emailMasked={}", maskEmail(command.email()));
                    return new AuthException(ErrorCode.AUTH_INVALID_CREDENTIALS);
                });

        if (!passwordEncoder.matches(command.password(), member.getPassword())) {
            log.warn("로그인 실패 - 비밀번호 불일치 memberId={}", member.getId());
            throw new AuthException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        TokenPair tokens = refreshTokenService.issueOnLogin(member);
        log.info("로그인 성공 memberId={}", member.getId());
        return tokens;
    }

    public TokenPair refresh(String rawRefreshToken) {
        return refreshTokenService.rotate(rawRefreshToken);
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    /**
     * 비밀번호 변경. 현재 비밀번호 BCrypt 검증 후 신규 해시로 교체하고 해당 회원의 활성 refresh 를 전부 무효화한다.
     * 현재 비밀번호 불일치는 MEMBER_PASSWORD_MISMATCH(400).
     */
    @Transactional
    public void changePassword(Long memberId, String currentPassword, String newPassword) {
        Member member = memberRepository.findByIdAndDeletedAtIsNull(memberId)
                .orElseThrow(MemberNotFoundException::new);

        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            log.warn("비밀번호 변경 실패 - 현재 비밀번호 불일치 memberId={}", memberId);
            throw new AuthException(ErrorCode.MEMBER_PASSWORD_MISMATCH);
        }

        member.changePassword(passwordEncoder.encode(newPassword));
        // [순서 불변] member.password UPDATE 는 flush 하지 않고 본 트랜잭션 커밋까지 미룬 뒤, revoke 를
        // REQUIRES_NEW 로 먼저 커밋한다. 이 순서를 "비번 먼저 확정" 으로 뒤집지 말 것:
        //   - revoke 의 REQUIRES_NEW 는 토큰 재사용 감지(RefreshTokenService.rotate)가 예외 throw 후에도
        //     무효화를 보존하려고 의존하므로 제거 불가.
        //   - 그렇다고 여기서 member 를 saveAndFlush 하면, outer 가 member 행 락을 쥔 채 REQUIRES_NEW 가
        //     refresh_token 을 revoke(자식→부모 FK fk_refresh_token_member 로 member 와 묶임)하면서 락
        //     경합·데드락(Lock wait timeout)을 일으킨다. 실측으로 병렬 스위트에서 1205/40001 재현됨.
        // 따라서 현재 순서가 유일하게 안전하다. 잔존 위험(revoke 커밋 후 outer 롤백 시 세션만 죽고 비번은
        // 옛값)은 fail-safe 방향(보안 약화 없음, 재로그인만 요구)이라 수용한다.
        int revoked = refreshTokenAdmin.forceRevokeAllActiveSessions(memberId, clock.instant());
        log.info("비밀번호 변경 성공 memberId={} revokedSessions={}", memberId, revoked);
    }

    /**
     * 로그 노출용 이메일 마스킹. local-part 의 첫 글자만 남기고 나머지를 별표로 치환한다.
     * 예: user@example.com → u***@example.com, a@x.com → a@x.com
     */
    private static String maskEmail(String email) {
        if (email == null) {
            return "null";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 1) {
            return local + domain;
        }
        return local.charAt(0) + "***" + domain;
    }
}
