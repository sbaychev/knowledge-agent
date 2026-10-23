package com.stefan.knowledgeagent.api;

import com.stefan.knowledgeagent.api.dto.ApiErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private ApiExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {

        handler = new ApiExceptionHandler();

        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/agent/chat");
    }

    @Test
    void mapsTransientProviderFailureToServiceUnavailable() {

        RuntimeException exception =
                new TransientAiException("Sensitive upstream details");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleAiProviderUnavailable(exception, request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code())
                .isEqualTo("AI_PROVIDER_UNAVAILABLE");
        assertThat(response.getBody().message())
                .isEqualTo(
                        "The AI provider is temporarily unavailable. Please try again."
                );
        assertThat(response.getBody().message())
                .doesNotContain("Sensitive upstream details");
        assertThat(response.getBody().path())
                .isEqualTo("/api/v1/agent/chat");
    }

    @Test
    void mapsNonTransientProviderFailureToBadGateway() {

        NonTransientAiException exception =
                new NonTransientAiException("Upstream rejected request");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleAiProviderRejected(exception, request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.BAD_GATEWAY);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code())
                .isEqualTo("AI_PROVIDER_ERROR");
        assertThat(response.getBody().message())
                .doesNotContain("Upstream rejected request");
    }
}
