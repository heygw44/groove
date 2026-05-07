package com.groove.auth.api;

import com.groove.auth.api.dto.LoginRequest;
import com.groove.auth.api.dto.LoginResponse;
import com.groove.auth.api.dto.LogoutRequest;
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
     * 로그아웃 엔드포인트.
     *
     * <p>본 이슈(#21) 범위에서는 RefreshToken 영속화·revoke 가 도입되기 전이므로
     * 입력 형식만 검증하고 200 을 반환한다. 실제 무효화 로직은 #22 에서 추가된다.
     * RFC 7009 와 같이 토큰 자체의 유효성과 무관하게 응답 코드는 항상 200 이다.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        // TODO(#22): RefreshTokenService.revoke(request.refreshToken()) 호출
        return ResponseEntity.ok().build();
    }
}
