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
        attrs.put("userId", "u-1");
        attrs.put("orderId", 123);

        assertThat(BizEventLogger.format("ORDER_CREATED", attrs))
                .isEqualTo("BIZ_EVENT type=ORDER_CREATED orderId=123 userId=u-1");
    }

    @Test
    void escapesTypeWithControlChars() {
        assertThat(BizEventLogger.format("evil\nfake", Map.of()))
                .isEqualTo("BIZ_EVENT type=\"evil\\nfake\"");
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
    void quotesValuesContainingWhitespaceOrSpecialChars() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("name", "John Doe");
        attrs.put("note", "has=equals");
        attrs.put("quoted", "say \"hi\"");

        assertThat(BizEventLogger.format("EVT", attrs))
                .isEqualTo("BIZ_EVENT type=EVT name=\"John Doe\" note=\"has=equals\" quoted=\"say \\\"hi\\\"\"");
    }

    @Test
    void escapesControlCharsToPreventLogForging() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("evil", "abc\nERROR fake");
        attrs.put("tabbed", "a\tb");
        attrs.put("cr", "x\ry");

        assertThat(BizEventLogger.format("EVT", attrs))
                .isEqualTo("BIZ_EVENT type=EVT cr=\"x\\ry\" evil=\"abc\\nERROR fake\" tabbed=\"a\\tb\"");
    }

    @Test
    void escapesUnsafeKeyCharacters() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("with space", "v");

        assertThat(BizEventLogger.format("EVT", attrs))
                .isEqualTo("BIZ_EVENT type=EVT \"with space\"=v");
    }

    @Test
    void rejectsBlankType() {
        assertThatThrownBy(() -> BizEventLogger.format(" ", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
