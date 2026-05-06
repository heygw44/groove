package com.groove.common.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcFilterTest {

    private final MdcFilter filter = new MdcFilter(() -> "generated-id");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void generatesRequestIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedDuringChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> capturedDuringChain.set(MDC.get(MdcKeys.REQUEST_ID));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(MdcKeys.REQUEST_ID_HEADER)).isEqualTo("generated-id");
        assertThat(capturedDuringChain.get()).isEqualTo("generated-id");
        assertThat(MDC.get(MdcKeys.REQUEST_ID)).isNull();
    }

    @Test
    void preservesIncomingRequestId() throws Exception {
        String incoming = UUID.randomUUID().toString();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(MdcKeys.REQUEST_ID_HEADER, incoming);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedDuringChain = new AtomicReference<>();

        FilterChain chain = (req, res) -> capturedDuringChain.set(MDC.get(MdcKeys.REQUEST_ID));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(MdcKeys.REQUEST_ID_HEADER)).isEqualTo(incoming);
        assertThat(capturedDuringChain.get()).isEqualTo(incoming);
    }

    @Test
    void clearsMdcEvenWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new RuntimeException("boom");
        };

        try {
            filter.doFilter(request, response, chain);
        } catch (Exception ignored) {
            // expected
        }

        assertThat(MDC.get(MdcKeys.REQUEST_ID)).isNull();
        assertThat(MDC.get(MdcKeys.USER_ID)).isNull();
    }
}
