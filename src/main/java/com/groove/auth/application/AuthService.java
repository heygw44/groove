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

import java.time.Instant;

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
    private final RefreshTokenAdmin refreshTokenAdmin;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            RefreshTokenAdmin refreshTokenAdmin
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenAdmin = refreshTokenAdmin;
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
     * 비밀번호 변경 (#77, API.md §3.2 — PATCH /members/me/password).
     *
     * <p>현재 비밀번호를 BCrypt 비교로 검증한 뒤 신규 해시로 교체하고, 해당 회원의 활성 refresh
     * 토큰을 전부 무효화해 재로그인을 강제한다. 액세스 토큰은 stateless 라 만료 전 무효화가 불가능하므로
     * refresh 토큰 revoke 가 유일한 로그아웃 수단이며, 그래서 비동기가 아닌 동기로 처리한다.
     *
     * <p>현재 비밀번호 불일치는 {@link ErrorCode#MEMBER_PASSWORD_MISMATCH}(400) 로 응답한다. 이미
     * 인증된 회원의 입력 오류이므로 401 이 아니다 — 401 은 클라이언트의 토큰 갱신 인터셉터를 잘못
     * 작동시킬 수 있다.
     *
     * <p>로그인과 달리 이 메서드는 더티 체킹 쓰기가 필요해 {@code @Transactional} 을 가진다(BCrypt
     * 가 트랜잭션 안에서 수행되나 저빈도 엔드포인트라 허용). 세션 무효화는
     * {@link RefreshTokenAdmin#forceRevokeAllActiveSessions}({@code REQUIRES_NEW}) 라 별도 커밋되며,
     * 과다 revoke 는 무해하므로(재로그인이면 끝) 본 트랜잭션 롤백과 독립적이어도 보안 문제가 없다.
     *
     * @throws MemberNotFoundException 활성 회원이 없음 (탈퇴 후 토큰 만료 전 윈도)
     * @throws AuthException          현재 비밀번호 불일치 ({@link ErrorCode#MEMBER_PASSWORD_MISMATCH})
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
        // member 의 password 변경은 일부러 여기서 flush 하지 않고 본 트랜잭션 커밋 시점까지 미룬다.
        // refresh_token.member_id 가 member 를 FK 로 참조하므로, member 행을 먼저 flush(=배타 락) 하면
        // 직후 REQUIRES_NEW revoke 의 `UPDATE refresh_token WHERE member_id=?` 가 부모 member 행 락을
        // 기다리다 lock-wait 데드락에 빠진다. 커밋 시점 flush 면 revoke 가 이미 커밋된 뒤라 락 충돌이 없다.
        // revoke 와 커밋 사이에는 던질 수 있는 코드가 없어 "revoke 후 비번 미반영" 윈도도 사실상 없다.
        int revoked = refreshTokenAdmin.forceRevokeAllActiveSessions(memberId, Instant.now());
        log.info("비밀번호 변경 성공 memberId={} revokedSessions={}", memberId, revoked);
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
