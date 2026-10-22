package com.stefan.knowledgeagent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentChatRequest(

        @NotBlank
        @Size(max = 4000)
        String message

) {
}
