package com.stefan.knowledgeagent.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-ID";

    public static final String MDC_KEY = "correlationId";

    private static final Pattern VALID_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String correlationId = resolveCorrelationId(request);

        MDC.put(MDC_KEY, correlationId);

        response.setHeader(HEADER_NAME, correlationId);

        try {

            filterChain.doFilter(request, response);

        } finally {

            MDC.remove(MDC_KEY);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {

        String suppliedId = request.getHeader(HEADER_NAME);

        if (suppliedId != null && VALID_ID.matcher(suppliedId).matches()) {
            return suppliedId;
        }

        return UUID.randomUUID().toString();
    }
}
