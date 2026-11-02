package com.stefan.knowledgeagent.api.dto;

import java.util.UUID;

public record AgentChatResponse(

        UUID conversationId,
        UUID executionId,
        String answer
) {
}
