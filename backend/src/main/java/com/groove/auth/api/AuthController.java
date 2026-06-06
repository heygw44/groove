package com.groove.auth.api;

import com.groove.auth.api.dto.LoginRequest;
import com.groove.auth.api.dto.LoginResponse;
import com.groove.auth.api.dto.LogoutRequest;
import com.groove.auth.api.dto.RefreshRequest;
import com.groove.auth.api.dto.RefreshResponse;
import com.groove.auth.api.dto.SignupRequest;
import com.groove.auth.api.dto.SignupResponse;
import com.groove.auth.application.AuthService;
import com.groove.auth.application.LoginCommand;
import com.groove.auth.application.TokenPair;
import com.groove.member.application.MemberService;
import com.groove.member.application.SignupCommand;
import com.groove.member.domain.Member;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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

    public AuthController(MemberService memberService, AuthService authService) {
        this.memberService = memberService;
        this.authService = authService;
    }

    @Operation(summary = "회원가입",
            description = "이메일·비밀번호·이름·전화번호로 신규 회원을 생성한다. 성공 시 Location 헤더에 생성된 회원 리소스 URI 를 담는다.")
    @ApiResponse(responseCode = "201", description = "회원가입 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (이메일 형식·비밀번호 정책 등)",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "이미 가입된 이메일",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "회원가입 Rate Limit 초과",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
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
            description = "이메일·비밀번호로 인증하고 access/refresh 토큰 페어를 발급한다. 발급된 accessToken 을 우측 상단 Authorize 에 넣으면 보호 엔드포인트를 try-out 할 수 있다.")
    @ApiResponse(responseCode = "200", description = "로그인 성공 — 토큰 페어 발급")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호 불일치",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "429", description = "로그인 Rate Limit 초과",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenPair tokens = authService.login(new LoginCommand(request.email(), request.password()));
        return ResponseEntity.ok(LoginResponse.from(tokens));
    }

    /**
     * Refresh Token 회전 엔드포인트 (#22).
     *
     * <p>새 access·refresh 페어를 발급하고 기존 refresh 는 즉시 revoke 한다.
     * 재사용·만료·형식 오류는 {@code GlobalExceptionHandler} 가 401 ProblemDetail 로 변환한다.
     */
    @Operation(summary = "토큰 갱신",
            description = "Refresh Token 을 회전해 새 access/refresh 페어를 발급하고 기존 refresh 는 즉시 revoke 한다. 재사용된 토큰은 401.")
    @ApiResponse(responseCode = "200", description = "갱신 성공 — 새 토큰 페어 발급")
    @ApiResponse(responseCode = "401", description = "refresh 토큰 무효 · 만료 · 재사용",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenPair tokens = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(RefreshResponse.from(tokens));
    }

    /**
     * 로그아웃 엔드포인트.
     *
     * <p>RFC 7009 § 2.2 — 토큰 유효성과 무관하게 항상 200. 정상 토큰이면 revoke 되고,
     * 형식 오류·만료·미존재는 멱등 무동작으로 끝난다.
     */
    @Operation(summary = "로그아웃",
            description = "Refresh Token 을 revoke 한다. RFC 7009 에 따라 토큰 유효성과 무관하게 항상 200 (멱등).")
    @ApiResponse(responseCode = "200", description = "로그아웃 처리됨 (멱등 — 무효 토큰도 200)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok().build();
    }
}
