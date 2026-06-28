package com.groove.member.api;

import com.groove.security.AuthPrincipal;
import com.groove.member.api.dto.MemberResponse;
import com.groove.member.api.dto.UpdateProfileRequest;
import com.groove.member.api.dto.WithdrawRequest;
import com.groove.member.application.MemberService;
import com.groove.member.domain.Member;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 본인 정보 조회·수정·탈퇴 (GET/PATCH/DELETE /members/me). 본인 식별은 @AuthenticationPrincipal AuthPrincipal
 * 로만 수행한다(경로에 memberId 미노출). DELETE 는 비밀번호 재확인 후 soft delete, 성공 시 204.
 */
@Tag(name = "회원", description = "로그인한 본인의 정보 조회·수정·탈퇴 (모두 인증 필요 — 경로에 memberId 미노출)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/members/me")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @Operation(summary = "내 정보 조회",
            description = "인증 토큰으로 식별된 본인의 회원 정보를 조회한다. 비밀번호는 절대 노출하지 않는다.")
    @ApiResponse(responseCode = "200", description = "내 정보 조회 성공")
    @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 미제공·만료·무효)")
    @ApiResponse(responseCode = "404", description = "활성 회원 없음 (탈퇴 후 토큰 만료 전 윈도)")
    @GetMapping
    public ResponseEntity<MemberResponse> getMyInfo(@AuthenticationPrincipal AuthPrincipal principal) {
        Member member = memberService.getMyInfo(principal.memberId());
        return ResponseEntity.ok(MemberResponse.from(member));
    }

    @Operation(summary = "내 정보 수정",
            description = "이름·전화번호를 부분 수정한다 (PATCH). 전송하지 않은 필드(null)는 변경되지 않으며, "
                    + "빈 문자열은 검증 대상이라 400 으로 거부된다.")
    @ApiResponse(responseCode = "200", description = "내 정보 수정 성공")
    @ApiResponse(responseCode = "400", description = "입력 검증 실패 (이름 길이·전화번호 형식 등)")
    @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 미제공·만료·무효)")
    @ApiResponse(responseCode = "404", description = "활성 회원 없음")
    @PatchMapping
    public ResponseEntity<MemberResponse> updateMyInfo(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        Member member = memberService.updateMyInfo(principal.memberId(), request.toCommand());
        return ResponseEntity.ok(MemberResponse.from(member));
    }

    @Operation(summary = "회원 탈퇴",
            description = "본인 비밀번호 재확인 후 회원을 탈퇴(soft delete)한다. 성공 시 본문 없이 204. "
                    + "이미 탈퇴한 회원의 재요청도 멱등하게 204 로 수렴한다.")
    @ApiResponse(responseCode = "204", description = "탈퇴 처리됨 (멱등 — 이미 탈퇴한 회원도 204)")
    @ApiResponse(responseCode = "400", description = "비밀번호 미입력 또는 현재 비밀번호 불일치")
    @ApiResponse(responseCode = "401", description = "인증 실패 (토큰 미제공·만료·무효)")
    @ApiResponse(responseCode = "404", description = "해당 회원 없음")
    @ApiResponse(responseCode = "409", description = "진행 중인 주문이 있어 탈퇴 불가")
    @DeleteMapping
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody WithdrawRequest request) {
        memberService.withdraw(principal.memberId(), request.password());
        return ResponseEntity.noContent().build();
    }
}
