package com.stefan.knowledgeagent.api;

import com.stefan.knowledgeagent.api.dto.AgentChatRequest;
import com.stefan.knowledgeagent.api.dto.AgentChatResponse;
import com.stefan.knowledgeagent.order.OrderSupportAgentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentChatController {

    private final OrderSupportAgentService agentService;

    public AgentChatController(OrderSupportAgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public AgentChatResponse chat(
            @Valid @RequestBody AgentChatRequest request
    ) {
        return new AgentChatResponse(
                agentService.answer(request.message())
        );
    }

}
