package com.stefan.knowledgeagent.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateOrderItemRequest(

        @NotBlank
        @Size(max = 100)
        String productId,

        @NotBlank
        @Size(max = 255)
        String productName,

        @Positive
        int quantity,

        @NotNull
        @DecimalMin("0.0000")
        @Digits(integer = 15, fraction = 4)
        BigDecimal unitPrice

) { }
