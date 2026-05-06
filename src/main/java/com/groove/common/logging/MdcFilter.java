package com.groove.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class MdcFilter extends OncePerRequestFilter {

    private final RequestIdGenerator requestIdGenerator;

    public MdcFilter(RequestIdGenerator requestIdGenerator) {
        this.requestIdGenerator = requestIdGenerator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        response.setHeader(MdcKeys.REQUEST_ID_HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.REQUEST_ID);
            MDC.remove(MdcKeys.USER_ID);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader(MdcKeys.REQUEST_ID_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            return incoming;
        }
        return requestIdGenerator.generate();
    }
}
