package com.stefan.knowledgeagent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRefundApprovalRequest(
        @NotBlank
        @Size(max = 500)
        String reason
) {
}
