package com.stefan.knowledgeagent.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentMemoryConfiguration {

    @Bean("agentChatMemory")
    public ChatMemory agentChatMemory(
            @Value("${agent.memory.max-messages:20}") int maxMessages
    ) {

        if (maxMessages < 2) {
            throw new IllegalArgumentException(
                    "agent.memory.max-messages must be at least 2"
            );
        }

        return MessageWindowChatMemory.builder()
                .maxMessages(maxMessages)
                .build();
    }
}
