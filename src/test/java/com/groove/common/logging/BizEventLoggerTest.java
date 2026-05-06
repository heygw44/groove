package com.groove.common.logging;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BizEventLoggerTest {

    @Test
    void formatsTypeOnlyWhenNoAttributes() {
        assertThat(BizEventLogger.format("ORDER_CREATED", null))
                .isEqualTo("BIZ_EVENT type=ORDER_CREATED");
        assertThat(BizEventLogger.format("ORDER_CREATED", Map.of()))
                .isEqualTo("BIZ_EVENT type=ORDER_CREATED");
    }

    @Test
    void appendsSortedAttributes() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("orderId", 123);
        attrs.put("userId", "u-1");

        assertThat(BizEventLogger.format("ORDER_CREATED", attrs))
                .isEqualTo("BIZ_EVENT type=ORDER_CREATED orderId=123 userId=u-1");
    }

    @Test
    void skipsNullOrBlankValues() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("a", "alpha");
        attrs.put("b", null);
        attrs.put("c", "");
        attrs.put("d", " ");

        assertThat(BizEventLogger.format("EVT", attrs))
                .isEqualTo("BIZ_EVENT type=EVT a=alpha");
    }

    @Test
    void rejectsBlankType() {
        assertThatThrownBy(() -> BizEventLogger.format(" ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
