package com.stefan.knowledgeagent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class ChatMemoryPersistenceIntegrationTest {

    @Autowired
    @Qualifier("agentChatMemory")
    private ChatMemory chatMemory;

    @Autowired
    private ChatMemoryRepository chatMemoryRepository;

    @Test
    void restoresConversationMessagesUsingANewMemoryInstance() {

        assertThat(chatMemoryRepository)
                .isInstanceOf(JdbcChatMemoryRepository.class);

        String conversationId = UUID.randomUUID().toString();

        try {
            chatMemory.add(
                    conversationId,
                    List.of(
                            new UserMessage(
                                    "What is the current status of order 1?"
                            ),
                            new AssistantMessage(
                                    "Order #1 is SHIPPED."
                            )
                    )
            );

            // Simulate a new memory object reading the same stored context.
            ChatMemory reloadedMemory =
                    MessageWindowChatMemory.builder()
                            .chatMemoryRepository(chatMemoryRepository)
                            .maxMessages(20)
                            .build();

            List<Message> restoredMessages =
                    reloadedMemory.get(conversationId);

            assertThat(restoredMessages)
                    .hasSize(2);

            assertThat(restoredMessages.get(0).getText())
                    .isEqualTo(
                            "What is the current status of order 1?"
                    );

            assertThat(restoredMessages.get(1).getText())
                    .isEqualTo("Order #1 is SHIPPED.");

        } finally {
            // Isolate this integration test from subsequent executions.
            chatMemory.clear(conversationId);
        }
    }

    @Test
    void appendsMessagesAfterReloadingConversationMemory() {
        String conversationId = UUID.randomUUID().toString();

        try {
            chatMemory.add(
                    conversationId,
                    List.of(
                            new UserMessage("What is the status of order 1?"),
                            new AssistantMessage("Order #1 is SHIPPED.")
                    )
            );

            ChatMemory reloadedMemory =
                    MessageWindowChatMemory.builder()
                            .chatMemoryRepository(chatMemoryRepository)
                            .maxMessages(20)
                            .build();

            reloadedMemory.add(
                    conversationId,
                    List.of(
                            new UserMessage("Which carrier is delivering it?")
                    )
            );

            List<Message> messages = reloadedMemory.get(conversationId);

            assertThat(messages)
                    .extracting(Message::getText)
                    .containsExactly(
                            "What is the status of order 1?",
                            "Order #1 is SHIPPED.",
                            "Which carrier is delivering it?"
                    );
        } finally {
            chatMemory.clear(conversationId);
        }
    }
}
