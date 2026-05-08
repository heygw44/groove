package com.groove.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(
        controllers = GlobalExceptionHandlerTest.StubController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                UserDetailsServiceAutoConfiguration.class,
                ServletWebSecurityAutoConfiguration.class})
@ContextConfiguration(classes = {GlobalExceptionHandlerTest.StubController.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    // ── BusinessException 계열 ───────────────────────────────────────────

    @Test
    @DisplayName("AuthException → 401, code=AUTH_001, application/problem+json")
    void authException() throws Exception {
        mockMvc.perform(get("/stub/auth-error"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("DomainException → 422, code=DOMAIN_003")
    void domainException() throws Exception {
        mockMvc.perform(get("/stub/domain-error"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("DOMAIN_003"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("ExternalException → 502, code=EXT_001")
    void externalException() throws Exception {
        mockMvc.perform(get("/stub/external-error"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("EXT_001"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    // ── Spring 내장 예외 ─────────────────────────────────────────────────

    @Test
    @DisplayName("@Valid 실패(MethodArgumentNotValidException) → 400, code=HTTP_400, application/problem+json")
    void validationFailed() throws Exception {
        mockMvc.perform(post("/stub/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("HTTP_400"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("JSON 파싱 실패(HttpMessageNotReadableException) → 400, code=HTTP_400, application/problem+json")
    void jsonParseFailed() throws Exception {
        mockMvc.perform(post("/stub/valid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("HTTP_400"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("존재하지 않는 경로 → 404, code=HTTP_404, application/problem+json")
    void notFound() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("HTTP_404"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    // ── Spring Security 예외 ─────────────────────────────────────────────

    @Test
    @DisplayName("AccessDeniedException → 403, code=AUTH_002")
    void accessDenied() throws Exception {
        mockMvc.perform(get("/stub/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_002"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("AuthenticationException → 401, code=AUTH_001")
    void authenticationException() throws Exception {
        mockMvc.perform(get("/stub/auth-exception"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTH_001"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    // ── 폴백 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ConstraintViolationException(@Validated PathVariable) → 400, code=VALID_001 + violations 배열")
    void constraintViolation() throws Exception {
        mockMvc.perform(get("/stub/positive/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALID_001"))
                .andExpect(jsonPath("$.violations").isArray())
                .andExpect(jsonPath("$.violations[0].field").exists())
                .andExpect(jsonPath("$.violations[0].message").exists())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("RuntimeException(폴백) → 500, code=SYSTEM_001")
    void genericException() throws Exception {
        mockMvc.perform(get("/stub/generic-error"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("SYSTEM_001"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.traceId").exists());
    }

    // ── 스텁 컨트롤러 ────────────────────────────────────────────────────

    @RestController
    @RequestMapping("/stub")
    @Validated
    static class StubController {

        @GetMapping("/auth-error")
        void authError() {
            throw new AuthException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        @GetMapping("/domain-error")
        void domainError() {
            throw new DomainException(ErrorCode.DOMAIN_RULE_VIOLATION, "재고가 부족합니다");
        }

        @GetMapping("/external-error")
        void externalError() {
            throw new ExternalException(ErrorCode.EXTERNAL_SERVICE_ERROR);
        }

        @PostMapping("/valid")
        void validEndpoint(@RequestBody @Valid StubRequest request) {
        }

        @GetMapping("/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("접근 거부");
        }

        @GetMapping("/auth-exception")
        void authenticationException() {
            throw new BadCredentialsException("인증 실패");
        }

        @GetMapping("/generic-error")
        void genericError() {
            throw new RuntimeException("예상치 못한 오류");
        }

        @GetMapping("/positive/{id}")
        void positiveOnly(@PathVariable @Positive Long id) {
        }
    }

    record StubRequest(@NotBlank String name) {
    }
}
