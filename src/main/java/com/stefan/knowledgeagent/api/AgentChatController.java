package com.stefan.knowledgeagent.api;

import com.stefan.knowledgeagent.agent.AgentChatApplicationService;
import com.stefan.knowledgeagent.api.dto.AgentChatRequest;
import com.stefan.knowledgeagent.api.dto.AgentChatResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent")
public class AgentChatController {

    private final AgentChatApplicationService chatApplicationService;

    public AgentChatController(
            AgentChatApplicationService chatApplicationService
    ) {

        this.chatApplicationService = chatApplicationService;

    }

    @PostMapping("/chat")
    public AgentChatResponse chat(
            @Valid @RequestBody AgentChatRequest request
    ) {

        return chatApplicationService.chat(request);

    }

}
