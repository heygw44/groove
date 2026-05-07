package com.groove.auth.application;

import com.groove.common.exception.AuthException;
import com.groove.common.exception.ErrorCode;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 인증 흐름 애플리케이션 서비스.
 *
 * <p>로그인은 활성 회원 조회 → 비밀번호 BCrypt 비교 → 토큰 발급 순으로 진행한다.
 * 이메일 미존재와 비밀번호 불일치는 모두 동일한 {@link ErrorCode#AUTH_INVALID_CREDENTIALS}
 * 응답으로 일원화해 사용자 열거 공격을 차단한다.
 *
 * <p>Soft delete 된 회원은 {@code findByEmailAndDeletedAtIsNull} 가 비어 있으므로
 * 이메일 미존재 케이스와 동일하게 처리된다.
 *
 * <p>토큰 발급·영속화 책임은 {@link RefreshTokenService} 에 위임한다 (#22 — Rotation/재사용 감지).
 * 컨트롤러는 본 서비스만 의존하도록 {@link #refresh(String)}/{@link #logout(String)}
 * 위임 메서드를 제공한다.
 *
 * <p>트랜잭션 경계: 로그인 흐름의 read·write 는 {@link RefreshTokenService#issueOnLogin} 만
 * 트랜잭션을 가진다. 본 서비스에는 {@code @Transactional} 을 두지 않아 BCrypt 비교(~100ms)
 * 동안 DB 커넥션을 점유하지 않는다.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    public TokenPair login(LoginCommand command) {
        Member member = memberRepository.findByEmailAndDeletedAtIsNull(command.email())
                .orElseThrow(() -> {
                    log.warn("로그인 실패 - 이메일 미존재 emailHash={}", maskEmail(command.email()));
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
     * 로그 노출용 이메일 마스킹. 미존재 이메일도 ELK/CloudWatch 로 평문 저장되지 않도록
     * local-part 의 첫 글자만 남기고 나머지를 별표로 치환한다.
     * 예: {@code user@example.com → u***@example.com}, {@code a@x.com → a@x.com}
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
