package com.stefan.knowledgeagent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecordPaymentRequest(

        @NotBlank
        @Size(max = 150)
        String providerReference
) {
}
