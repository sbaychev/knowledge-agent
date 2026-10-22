package com.stefan.knowledgeagent.api;

import com.stefan.knowledgeagent.conversation.ConversationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(
            ConversationService conversationService
    ) {

        this.conversationService = conversationService;
    }

    @PostMapping("/{conversationId}/close")
    public ResponseEntity<Void> close(
            @PathVariable UUID conversationId
    ) {

        conversationService.close(conversationId);

        return ResponseEntity.noContent().build();
    }
}
