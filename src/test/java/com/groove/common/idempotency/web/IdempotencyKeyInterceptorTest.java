package com.groove.common.idempotency.web;

import com.groove.common.idempotency.exception.IdempotencyKeyRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IdempotencyKeyInterceptor — @Idempotent 핸들러 헤더 검증")
class IdempotencyKeyInterceptorTest {

    private final IdempotencyKeyInterceptor interceptor = new IdempotencyKeyInterceptor();
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("@Idempotent 핸들러 + 유효한 헤더 — 통과, 검증된 키를 요청 속성으로 노출")
    void idempotentHandler_validHeader_passesAndExposesKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdempotencyKeyInterceptor.HEADER, "key-123");

        boolean proceed = interceptor.preHandle(request, response, handler("annotated"));

        assertThat(proceed).isTrue();
        assertThat(request.getAttribute(IdempotencyKeyInterceptor.KEY_ATTRIBUTE)).isEqualTo("key-123");
    }

    @Test
    @DisplayName("@Idempotent 핸들러 + 헤더 누락 — 400")
    void idempotentHandler_missingHeader_rejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("annotated")))
                .isInstanceOf(IdempotencyKeyRequiredException.class);
        assertThat(request.getAttribute(IdempotencyKeyInterceptor.KEY_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("@Idempotent 핸들러 + 형식 위반 헤더 — 400")
    void idempotentHandler_malformedHeader_rejected() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IdempotencyKeyInterceptor.HEADER, "has space");

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("annotated")))
                .isInstanceOf(IdempotencyKeyRequiredException.class);
    }

    @Test
    @DisplayName("@Idempotent 없는 핸들러 — 헤더 없이도 통과, 속성 미설정")
    void plainHandler_noHeader_passes() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();

        boolean proceed = interceptor.preHandle(request, response, handler("plain"));

        assertThat(proceed).isTrue();
        assertThat(request.getAttribute(IdempotencyKeyInterceptor.KEY_ATTRIBUTE)).isNull();
    }

    @Test
    @DisplayName("HandlerMethod 가 아닌 핸들러(정적 리소스 등) — 통과")
    void nonHandlerMethod_passes() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        boolean proceed = interceptor.preHandle(request, response, new Object());

        assertThat(proceed).isTrue();
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        Method method = SampleController.class.getMethod(methodName);
        return new HandlerMethod(new SampleController(), method);
    }

    static class SampleController {

        @Idempotent
        public void annotated() {
        }

        public void plain() {
        }
    }
}
