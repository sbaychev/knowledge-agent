package com.stefan.knowledgeagent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AgentChatRequest(

        @NotBlank
        @Size(max = 4000)
        String message,

        UUID conversationId
) {
}
