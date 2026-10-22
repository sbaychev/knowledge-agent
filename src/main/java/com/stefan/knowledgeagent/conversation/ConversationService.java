package com.stefan.knowledgeagent.conversation;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository repository;
    private final ChatMemory chatMemory;

    public ConversationService(ConversationRepository repository,
                               @Qualifier("agentChatMemory") ChatMemory chatMemory) {
        this.repository = repository;
        this.chatMemory = chatMemory;
    }

    @Transactional
    public UUID getOrCreateActiveId(UUID requestedId) {

        if (requestedId == null) {

            Conversation conversation =
                    Conversation.start(UUID.randomUUID());

            return repository.save(conversation).getId();
        }

        Conversation conversation = repository.findById(requestedId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Conversation not found"
                ));

        if (!conversation.isActive()) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Conversation is closed"
            );
        }

        // making sure
        conversation.touch();

        return conversation.getId();
    }

    @Transactional
    public void close(UUID conversationId) {

        Conversation conversation = repository.findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Conversation not found"
                ));

        conversation.close();

        // Explicit save makes the intended persistence operation clear.
        repository.save(conversation);

        // Remove in-memory chat history for this conversation.
        chatMemory.clear(conversationId.toString());
    }
}
