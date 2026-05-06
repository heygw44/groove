package com.groove.common.exception;

import com.groove.common.logging.MdcKeys;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

import java.time.Instant;
import java.util.UUID;

public final class ProblemDetailEnricher {

    private ProblemDetailEnricher() {
    }

    public static void enrich(ProblemDetail pd, int statusCode) {
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("traceId", traceId());
        if (!pd.getProperties().containsKey("code")) {
            pd.setProperty("code", "HTTP_" + statusCode);
        }
    }

    private static String traceId() {
        String mdc = MDC.get(MdcKeys.REQUEST_ID);
        return mdc != null ? mdc : UUID.randomUUID().toString();
    }
}
