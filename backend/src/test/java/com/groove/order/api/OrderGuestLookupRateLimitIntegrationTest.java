package com.groove.order.api;

import com.groove.support.TestcontainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 게스트 주문 조회 Rate Limit 통합 테스트 — IP당 한도 초과 시 429, IP 간 버킷 독립.
 * capacity=3 으로 override 하고, 미존재 주문번호로 호출해 토큰 소진 전 404·소진 후 429 를 관찰한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = "groove.order.rate-limit.guest-lookup.capacity=3")
@DisplayName("POST /orders/{orderNumber}/guest-lookup Rate Limit (#208)")
class OrderGuestLookupRateLimitIntegrationTest {

    private static final String PATH = "/api/v1/orders/ORD-20260101-AAAAAA/guest-lookup";
    private static final String BODY = "{\"email\":\"guest@example.com\"}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("IP당 capacity(3) 초과 → 4번째 429, 다른 IP 버킷은 독립")
    void perIpLimit_exceeded_returns429() throws Exception {
        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post(PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(BODY))
                    .andExpect(status().isNotFound());
        }

        mockMvc.perform(post(PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isTooManyRequests());

        // 다른 IP 는 자신의 버킷을 가지므로 한도와 무관하게 통과(404).
        mockMvc.perform(post(PATH)
                        .with(remoteAddr("198.51.100.50"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNotFound());
    }

    private static RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
