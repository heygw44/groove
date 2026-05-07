package com.groove.auth.application;

import com.groove.auth.security.JwtProperties;
import com.groove.auth.security.JwtProvider;
import com.groove.common.exception.AuthException;
import com.groove.common.exception.ErrorCode;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 흐름 애플리케이션 서비스.
 *
 * <p>로그인은 활성 회원 조회 → 비밀번호 BCrypt 비교 → 토큰 발급 순으로 진행한다.
 * 이메일 미존재와 비밀번호 불일치는 모두 동일한 {@link ErrorCode#AUTH_INVALID_CREDENTIALS}
 * 응답으로 일원화해 사용자 열거 공격을 차단한다.
 *
 * <p>Soft delete 된 회원은 {@code findByEmailAndDeletedAtIsNull} 가 비어 있으므로
 * 이메일 미존재 케이스와 동일하게 처리된다.
 */
@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            JwtProvider jwtProvider,
            JwtProperties jwtProperties
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.jwtProperties = jwtProperties;
    }

    @Transactional(readOnly = true)
    public TokenPair login(LoginCommand command) {
        Member member = memberRepository.findByEmailAndDeletedAtIsNull(command.email())
                .orElseThrow(() -> new AuthException(ErrorCode.AUTH_INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(command.password(), member.getPassword())) {
            throw new AuthException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        String accessToken = jwtProvider.issueAccessToken(member.getId(), member.getRole());
        String refreshToken = jwtProvider.issueRefreshToken(member.getId());

        return new TokenPair(accessToken, refreshToken, jwtProperties.accessTokenTtlSeconds());
    }
}
