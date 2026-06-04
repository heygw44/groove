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
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final MemberService memberService;
    private final AuthService authService;

    public AuthController(MemberService memberService, AuthService authService) {
        this.memberService = memberService;
        this.authService = authService;
    }

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
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.ok().build();
    }
}
