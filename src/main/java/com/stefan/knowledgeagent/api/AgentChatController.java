package com.stefan.knowledgeagent.api;

import com.stefan.knowledgeagent.api.dto.AgentChatRequest;
import com.stefan.knowledgeagent.api.dto.AgentChatResponse;
import com.stefan.knowledgeagent.conversation.ConversationService;
import com.stefan.knowledgeagent.order.OrderSupportAgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentChatController {

    private final OrderSupportAgentService orderSupportAgentService;
    private final ConversationService conversationService;

    public AgentChatController(OrderSupportAgentService orderSupportAgentService, ConversationService conversationService) {
        this.orderSupportAgentService = orderSupportAgentService;
        this.conversationService = conversationService;

    }

    @PostMapping("/chat")
    public AgentChatResponse chat(
            @Valid @RequestBody AgentChatRequest request
    ) {

        UUID conversationId =
                conversationService.getOrCreateActiveId(
                        request.conversationId()
                );

        String answer = orderSupportAgentService.answer(
                request.message(),
                conversationId.toString()
        );

        return new AgentChatResponse(conversationId, answer);
    }

}
