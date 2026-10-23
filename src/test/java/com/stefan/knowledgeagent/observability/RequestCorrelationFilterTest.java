package com.stefan.knowledgeagent.observability;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter =
            new RequestCorrelationFilter();

    @Test
    void propagatesValidCorrelationIdAndAddsResponseHeader()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                RequestCorrelationFilter.HEADER_NAME,
                "observability-check-001"
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(
                RequestCorrelationFilter.HEADER_NAME
        )).isEqualTo("observability-check-001");

        // MDC must be cleaned up after processing the request.
        assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesInvalidCorrelationIdWithGeneratedUuid()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                RequestCorrelationFilter.HEADER_NAME,
                "invalid correlation id with spaces"
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        String correlationId = response.getHeader(
                RequestCorrelationFilter.HEADER_NAME
        );

        assertThat(correlationId)
                .isNotBlank()
                .matches(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                                + "[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
                                + "[0-9a-fA-F]{12}"
                );

        assertThat(MDC.get(RequestCorrelationFilter.MDC_KEY)).isNull();
    }
}
