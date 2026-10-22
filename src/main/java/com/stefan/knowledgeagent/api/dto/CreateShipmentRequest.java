package com.stefan.knowledgeagent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateShipmentRequest(

        @NotBlank
        @Size(max = 100)
        String carrier,

        @NotBlank
        @Size(max = 150)
        String trackingNumber

) { }
