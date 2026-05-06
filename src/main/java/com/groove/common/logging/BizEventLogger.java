package com.groove.common.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class BizEventLogger {

    private static final String LOGGER_NAME = "BIZ_EVENT";
    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);
    private static final String PREFIX = "BIZ_EVENT";
    private static final Pattern UNQUOTED_SAFE = Pattern.compile("[A-Za-z0-9._:\\-/]+");
    private static final Pattern KEY_SAFE = Pattern.compile("[A-Za-z0-9._\\-]+");

    private BizEventLogger() {
    }

    public static void log(String type, Map<String, ?> attributes) {
        log.info(format(type, attributes));
    }

    static String format(String type, Map<String, ?> attributes) {
        Objects.requireNonNull(type, "type must not be null");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        Map<String, ?> safeAttrs = attributes == null ? Map.of() : attributes;

        String tail = safeAttrs.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().isBlank())
                .filter(e -> e.getValue() != null && !String.valueOf(e.getValue()).isBlank())
                .sorted(Map.Entry.comparingByKey())
                .map(e -> renderKey(e.getKey()) + "=" + renderValue(String.valueOf(e.getValue())))
                .collect(Collectors.joining(" "));

        if (tail.isEmpty()) {
            return PREFIX + " type=" + type;
        }
        return PREFIX + " type=" + type + " " + tail;
    }

    public static Map<String, Object> attrs() {
        return new LinkedHashMap<>();
    }

    private static String renderKey(String key) {
        if (KEY_SAFE.matcher(key).matches()) {
            return key;
        }
        return "\"" + escape(key) + "\"";
    }

    private static String renderValue(String value) {
        String sanitized = escape(value);
        if (UNQUOTED_SAFE.matcher(value).matches() && sanitized.equals(value)) {
            return value;
        }
        return "\"" + sanitized + "\"";
    }

    private static String escape(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (Character.isISOControl(c)) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
