package com.groove.auth.api;

import com.groove.member.domain.Member;
import com.groove.member.domain.MemberRepository;
import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@DisplayName("POST /api/v1/auth/signup")
class AuthControllerSignupTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanup() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("정상 요청 → 201 + Location + 응답 본문, DB에 BCrypt 해시 저장")
    void signup_success() throws Exception {
        Map<String, String> body = Map.of(
                "email", "user@example.com",
                "password", "P@ssw0rd!2024",
                "name", "김철수",
                "phone", "01012345678"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.memberId").exists())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.name").value("김철수"))
                .andExpect(jsonPath("$.createdAt").exists());

        Optional<Member> persisted = memberRepository.findByEmailAndDeletedAtIsNull("user@example.com");
        assertThat(persisted).isPresent();
        Member member = persisted.get();
        assertThat(member.getPassword())
                .as("DB에 평문 저장 금지")
                .isNotEqualTo("P@ssw0rd!2024");
        assertThat(passwordEncoder.matches("P@ssw0rd!2024", member.getPassword()))
                .as("저장된 해시는 원본 비밀번호와 매칭되어야 함")
                .isTrue();
    }

    @Test
    @DisplayName("이메일 중복 → 409 ProblemDetail + MEMBER_EMAIL_DUPLICATED")
    void signup_duplicatedEmail_returns409() throws Exception {
        Map<String, String> body = Map.of(
                "email", "dup@example.com",
                "password", "P@ssw0rd!2024",
                "name", "최초",
                "phone", "01011112222"
        );
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        Map<String, String> dup = Map.of(
                "email", "dup@example.com",
                "password", "Different!9876",
                "name", "두번째",
                "phone", "01033334444"
        );
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("MEMBER_EMAIL_DUPLICATED"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("비밀번호 정책 위반 (특수문자 없음) → 400")
    void signup_weakPassword_returns400() throws Exception {
        Map<String, String> body = Map.of(
                "email", "weak@example.com",
                "password", "abcd1234ef",
                "name", "홍길동",
                "phone", "01012345678"
        );
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("필수 필드 누락 (이름) → 400")
    void signup_missingName_returns400() throws Exception {
        Map<String, String> body = Map.of(
                "email", "u@example.com",
                "password", "P@ssw0rd!2024",
                "phone", "01012345678"
        );
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("이메일 형식 오류 → 400")
    void signup_invalidEmail_returns400() throws Exception {
        Map<String, String> body = Map.of(
                "email", "not-an-email",
                "password", "P@ssw0rd!2024",
                "name", "홍길동",
                "phone", "01012345678"
        );
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("전화번호 누락 → 400 (필수 필드)")
    void signup_missingPhone_returns400() throws Exception {
        Map<String, String> body = Map.of(
                "email", "nophone@example.com",
                "password", "P@ssw0rd!2024",
                "name", "홍길동"
        );
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
