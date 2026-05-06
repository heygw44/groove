package com.groove.auth.api;

import com.groove.auth.api.dto.SignupRequest;
import com.groove.auth.api.dto.SignupResponse;
import com.groove.member.application.MemberService;
import com.groove.member.application.SignupCommand;
import com.groove.member.domain.Member;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    public AuthController(MemberService memberService) {
        this.memberService = memberService;
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
}
