package com.groove.auth.api;

import com.groove.auth.api.dto.LoginRequest;
import com.groove.auth.api.dto.LoginResponse;
import com.groove.auth.api.dto.RefreshResponse;
import com.groove.auth.api.dto.SignupRequest;
import com.groove.auth.api.dto.SignupResponse;
import com.groove.auth.application.AuthService;
import com.groove.auth.application.LoginCommand;
import com.groove.auth.application.TokenPair;
import com.groove.auth.security.RefreshTokenCookieFactory;
import com.groove.common.exception.AuthException;
import com.groove.common.exception.ErrorCode;
import com.groove.member.application.MemberService;
import com.groove.member.application.SignupCommand;
import com.groove.member.domain.Member;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Tag(name = "인증", description = "회원가입 · 로그인 · 토큰 갱신 · 로그아웃 (모두 비로그인 공개 엔드포인트)")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final MemberService memberService;
    private final AuthService authService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    public AuthController(MemberService memberService,
                          AuthService authService,
                          RefreshTokenCookieFactory refreshTokenCookieFactory) {
        this.memberService = memberService;
        this.authService = authService;
        this.refreshTokenCookieFactory = refreshTokenCookieFactory;
    }

    @Operation(summary = "회원가입",
            description = "이메일·비밀번호·이름·전화번호로 신규 회원을 생성한다. 성공 시 Location 헤더에 생성된 회원 리소스 URI 를 담는다.")
    @ApiResponse(responseCode = "201", description = "회원가입 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (이메일 형식·비밀번호 정책 등)")
    @ApiResponse(responseCode = "409", description = "이미 가입된 이메일")
    @ApiResponse(responseCode = "429", description = "회원가입 Rate Limit 초과")
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupCommand command = new SignupCommand(
                request.email(),
                request.password(),
                request.name(),
                request.phone()
        );
        Member member = memberService.signup(command);
        SignupResponse body = SignupResponse.from(member);

        URI location = UriComponentsBuilder
                .fromPath("/api/v1/members/{id}")
                .buildAndExpand(member.getId())
                .toUri();
        return ResponseEntity.created(location).body(body);
    }

    @Operation(summary = "로그인",
            description = "이메일·비밀번호로 인증하고 accessToken 을 발급한다. refresh 토큰은 응답 body 가 아닌 "
                    + "HttpOnly 쿠키(refreshToken)로 내려가 JS 접근이 차단된다(#163). 발급된 accessToken 을 우측 "
                    + "상단 Authorize 에 넣으면 보호 엔드포인트를 try-out 할 수 있다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공 — accessToken 발급 + refresh 토큰 HttpOnly 쿠키 설정")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패")
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치")
    @ApiResponse(responseCode = "429", description = "로그인 Rate Limit 초과")
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenPair tokens = authService.login(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(tokens.refreshToken()).toString())
                .body(LoginResponse.from(tokens));
    }

    /**
     * Refresh Token 회전 엔드포인트 (#22, #163 쿠키 전환).
     *
     * <p>refresh 토큰은 HttpOnly 쿠키({@link RefreshTokenCookieFactory#COOKIE_NAME})로 수신한다.
     * 새 access 토큰을 body 로, 회전된 새 refresh 토큰을 다시 HttpOnly 쿠키로 내린다.
     * 쿠키 미존재는 무효 토큰과 동일하게 401 로 응답하고, 재사용·만료·형식 오류는
     * {@code GlobalExceptionHandler} 가 401 ProblemDetail 로 변환한다.
     */
    @Operation(summary = "토큰 갱신",
            description = "HttpOnly 쿠키(refreshToken)의 토큰을 회전해 새 accessToken 을 body 로 발급하고, 회전된 새 "
                    + "refresh 토큰을 다시 HttpOnly 쿠키로 내린다. 기존 refresh 는 즉시 revoke 된다. 쿠키 미존재·재사용된 토큰은 401.")
    @ApiResponse(responseCode = "200", description = "갱신 성공 — 새 accessToken 발급 + refresh 쿠키 회전")
    @ApiResponse(responseCode = "401", description = "refresh 쿠키 없음 · 무효 · 만료 · 재사용")
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        TokenPair tokens = authService.refresh(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(tokens.refreshToken()).toString())
                .body(RefreshResponse.from(tokens));
    }

    /**
     * 로그아웃 엔드포인트 (#163 쿠키 전환).
     *
     * <p>refresh 토큰은 HttpOnly 쿠키로 수신한다. RFC 7009 § 2.2 — 토큰 유효성과 무관하게 항상 200.
     * 쿠키가 있으면 revoke 하고, 없거나 형식 오류·만료·미존재면 멱등 무동작으로 끝난다.
     * 어느 경우든 응답에 Max-Age=0 쿠키를 실어 브라우저의 refresh 쿠키를 즉시 삭제한다.
     */
    @Operation(summary = "로그아웃",
            description = "HttpOnly 쿠키(refreshToken)를 revoke 하고 쿠키를 삭제한다. RFC 7009 에 따라 토큰 유효성과 무관하게 항상 200 (멱등).")
    @ApiResponse(responseCode = "200", description = "로그아웃 처리됨 (멱등 — 쿠키 없음·무효 토큰도 200)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
                .build();
    }
}
