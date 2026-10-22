package com.stefan.knowledgeagent.api;

import com.stefan.knowledgeagent.api.dto.ApiErrorResponse;
import com.stefan.knowledgeagent.common.exception.ResourceNotFoundException;
import com.stefan.knowledgeagent.order.exception.InvalidOrderStateException;
import com.stefan.knowledgeagent.payment.exception.InvalidPaymentStateException;
import com.stefan.knowledgeagent.payment.exception.InvalidRefundStateException;
import com.stefan.knowledgeagent.payment.exception.RefundAmountExceededException;
import com.stefan.knowledgeagent.payment.exception.RefundIdempotencyConflictException;
import com.stefan.knowledgeagent.shipment.exception.InvalidShipmentStateException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            ResourceNotFoundException exception,
            HttpServletRequest request) {

        return error(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler({
            InvalidOrderStateException.class,
            InvalidShipmentStateException.class,
            InvalidPaymentStateException.class,
            InvalidRefundStateException.class,
            RefundIdempotencyConflictException.class
    })
    public ResponseEntity<ApiErrorResponse> handleConflict(
            RuntimeException exception,
            HttpServletRequest request) {

        return error(
                HttpStatus.CONFLICT,
                "BUSINESS_CONFLICT",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(RefundAmountExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRefundAmountExceeded(
            RefundAmountExceededException exception,
            HttpServletRequest request) {

        return error(
                HttpStatus.UNPROCESSABLE_CONTENT,
                "REFUND_AMOUNT_EXCEEDED",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {

        Map<String, String> fieldErrors = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(fieldError ->
                fieldErrors.putIfAbsent(
                        fieldError.getField(),
                        fieldError.getDefaultMessage() == null
                                ? "Invalid value"
                                : fieldError.getDefaultMessage()
                )
        );

        return error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "One or more request fields are invalid",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {

        return error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MissingRequestHeaderException.class
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(
            Exception exception,
            HttpServletRequest request) {

        return error(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                exception.getMessage(),
                request,
                Map.of()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleIntegrityConflict(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {

        return error(
                HttpStatus.CONFLICT,
                "DATA_CONFLICT",
                "The request conflicts with existing data or a database constraint",
                request,
                Map.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {

        log.error("Unhandled API error for {}", request.getRequestURI(), exception);

        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected server error occurred",
                request,
                Map.of()
        );
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors) {

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message == null ? status.getReasonPhrase() : message,
                request.getRequestURI(),
                fieldErrors
        );

        return ResponseEntity.status(status).body(body);
    }

}
