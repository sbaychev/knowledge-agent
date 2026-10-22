package com.stefan.knowledgeagent.api;

import com.stefan.knowledgeagent.common.exception.AgentInputTooLongException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgentExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AgentExceptionHandler.class);

    @ExceptionHandler(AgentInputTooLongException.class)
    public ResponseEntity<AgentError> handleInputTooLong(
            AgentInputTooLongException exception
    ) {
        return ResponseEntity.badRequest().body(
                new AgentError(
                        "AGENT_INPUT_TOO_LONG",
                        "Message exceeds the maximum length of "
                                + exception.getMaxCharacters()
                                + " characters.",
                        Instant.now()
                )
        );
    }

    @ExceptionHandler(TransientAiException.class)
    public ResponseEntity<AgentError> handleTransientAiFailure(
            TransientAiException exception
    ) {

        log.warn("Temporary AI provider failure", exception);

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                new AgentError(
                        "LLM_TEMPORARILY_UNAVAILABLE",
                        "The AI service is temporarily unavailable. Please retry.",
                        Instant.now()
                )
        );
    }

    @ExceptionHandler(NonTransientAiException.class)
    public ResponseEntity<AgentError> handleNonTransientAiFailure(
            NonTransientAiException exception
    ) {

        log.error("Non-transient AI provider failure", exception);

        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(
                new AgentError(
                        "LLM_PROVIDER_ERROR",
                        "The AI service could not process the request.",
                        Instant.now()
                )
        );
    }

    public record AgentError(
            String code,
            String message,
            Instant timestamp
    ) {
    }
}
