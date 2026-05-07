package com.groove.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class RequestPathsTest {

    @Test
    void returnsPathAsIsWhenAlreadyNormalized() {
        assertThat(RequestPaths.normalizedPath(requestWithUri("/api/v1/auth/login")))
                .isEqualTo("/api/v1/auth/login");
    }

    @Test
    void collapsesConsecutiveSlashes() {
        assertThat(RequestPaths.normalizedPath(requestWithUri("/api/v1/auth//login")))
                .isEqualTo("/api/v1/auth/login");
        assertThat(RequestPaths.normalizedPath(requestWithUri("////api/v1/auth/login")))
                .isEqualTo("/api/v1/auth/login");
        assertThat(RequestPaths.normalizedPath(requestWithUri("/api////v1////auth////login")))
                .isEqualTo("/api/v1/auth/login");
    }

    @Test
    void resolvesDotSegment() {
        assertThat(RequestPaths.normalizedPath(requestWithUri("/api/v1/auth/./login")))
                .isEqualTo("/api/v1/auth/login");
    }

    @Test
    void resolvesDotDotSegment() {
        assertThat(RequestPaths.normalizedPath(requestWithUri("/api/v1/auth/foo/../login")))
                .isEqualTo("/api/v1/auth/login");
    }

    @Test
    void stripsMatrixParameters() {
        assertThat(RequestPaths.normalizedPath(requestWithUri("/api/v1/auth/login;jsessionid=abc")))
                .isEqualTo("/api/v1/auth/login");
    }

    @Test
    void emptyUriYieldsEmptyString() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("");
        assertThat(RequestPaths.normalizedPath(request)).isEmpty();
    }

    @Test
    void preservesQueryDelimiterIsNotInRequestUri() {
        // getRequestURI 는 query string 을 포함하지 않으므로 별도 처리는 불필요하다.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login");
        request.setQueryString("foo=bar");
        assertThat(RequestPaths.normalizedPath(request)).isEqualTo("/api/v1/auth/login");
    }

    private static MockHttpServletRequest requestWithUri(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(uri);
        return request;
    }
}
