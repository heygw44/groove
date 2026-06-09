package com.groove.member.api;

import com.groove.auth.domain.RefreshTokenRepository;
import com.groove.auth.security.JwtProvider;
import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.member.domain.MemberRole;
import com.groove.support.MemberFixtures;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("/api/v1/members/me 내 정보 조회·수정 API")
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static final String RAW_PASSWORD = "P@ssw0rd!2024";

    private String userBearer;
    private Long memberId;

    @BeforeEach
    void setUp() {
        // refresh_token 이 member 에 FK(비-cascade)를 가지므로 자식부터 삭제한다.
        refreshTokenRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();

        Member member = memberRepository.saveAndFlush(
                MemberFixtures.register("user@example.com", passwordEncoder.encode(RAW_PASSWORD), "김철수", "01012345678"));
        memberId = member.getId();
        userBearer = "Bearer " + jwtProvider.issueAccessToken(memberId, MemberRole.USER);
    }

    @Test
    @DisplayName("GET: 인증된 회원 → 200, 본인 정보 반환 (password 미노출)")
    void getMyInfo_authenticated_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, userBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(memberId))
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.name").value("김철수"))
                .andExpect(jsonPath("$.phone").value("01012345678"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("GET: 미인증 → 401")
    void getMyInfo_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH: name·phone 수정 → 200, 응답·DB 모두 반영")
    void updateMyInfo_bothFields_returns200() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김영희\",\"phone\":\"01098765432\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김영희"))
                .andExpect(jsonPath("$.phone").value("01098765432"));

        Member updated = memberRepository.findById(memberId).orElseThrow();
        assertThat(updated.getName()).isEqualTo("김영희");
        assertThat(updated.getPhone()).isEqualTo("01098765432");
    }

    @Test
    @DisplayName("PATCH: name 만 전송 → phone 미변경 (부분 수정)")
    void updateMyInfo_partial_keepsPhone() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김영희\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김영희"))
                .andExpect(jsonPath("$.phone").value("01012345678"));
    }

    @Test
    @DisplayName("PATCH: email·role 은 요청에 있어도 변경되지 않음 (화이트리스트 밖)")
    void updateMyInfo_ignoresImmutableFields() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김영희\",\"email\":\"hacker@evil.com\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    @DisplayName("PATCH: 빈 본문({}) → 200, 아무것도 변경되지 않음 (전 필드 null = no-op)")
    void updateMyInfo_emptyBody_returns200AndNoChange() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("김철수"))
                .andExpect(jsonPath("$.phone").value("01012345678"));

        Member unchanged = memberRepository.findById(memberId).orElseThrow();
        assertThat(unchanged.getName()).isEqualTo("김철수");
        assertThat(unchanged.getPhone()).isEqualTo("01012345678");
    }

    @Test
    @DisplayName("PATCH: 잘못된 phone 형식 → 400 (ProblemDetail)")
    void updateMyInfo_invalidPhone_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH: 미인증 → 401")
    void updateMyInfo_unauthenticated_returns401() throws Exception {
        mockMvc.perform(patch("/api/v1/members/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"김영희\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE: 본인 비밀번호 일치 → 204, deleted_at 기록 (soft delete)")
    void withdraw_validPassword_returns204AndSoftDeletes() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        Member withdrawn = memberRepository.findById(memberId).orElseThrow();
        assertThat(withdrawn.isWithdrawn()).isTrue();
        assertThat(withdrawn.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("DELETE: 비밀번호 불일치 → 400, 탈퇴되지 않음")
    void withdraw_wrongPassword_returns400() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"WrongPass!99\"}"))
                .andExpect(status().isBadRequest());

        Member unchanged = memberRepository.findById(memberId).orElseThrow();
        assertThat(unchanged.isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("DELETE: 비밀번호 누락(blank) → 400 (검증 실패)")
    void withdraw_blankPassword_returns400() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        Member unchanged = memberRepository.findById(memberId).orElseThrow();
        assertThat(unchanged.isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("DELETE: 미인증 → 401")
    void withdraw_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE: 재탈퇴(이미 탈퇴) → 멱등 204, 최초 deleted_at 유지")
    void withdraw_repeated_isIdempotent() throws Exception {
        mockMvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        var firstDeletedAt = memberRepository.findById(memberId).orElseThrow().getDeletedAt();

        // access token 은 stateless 라 탈퇴 직후에도 만료 전까지 유효 — 같은 토큰으로 재요청
        mockMvc.perform(delete("/api/v1/members/me")
                        .header(HttpHeaders.AUTHORIZATION, userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + RAW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(memberRepository.findById(memberId).orElseThrow().getDeletedAt())
                .isEqualTo(firstDeletedAt);
    }
}
