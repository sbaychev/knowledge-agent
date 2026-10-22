package com.stefan.knowledgeagent.order;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OrderSupportAgentService {

    private static final String SYSTEM_PROMPT = """
            You are the order-support assistant for "Knowledge Agent" app.
            
            You can look up orders using the lookup_order tool.
            
            Rules:
            1. Use the tool when the user asks about a specific order and
               provides its numeric order ID.
            2. Never invent order details, statuses, customer IDs, amounts,
               products, or results.
            3. If the user has not provided enough information to identify an
               order, ask a concise clarifying question.
            4. If the tool reports that an order does not exist, state that
               clearly. Do not guess another order ID.
            5. The tool only provides order information. Do not claim to know
               shipment tracking, payment or refund status from this result.
            6. You are read-only. Never claim to have modified an order,
               issued a refund, or performed any other transaction.
            7. Answer clearly and directly, preferably in the user's language.
            """;

    private final ChatClient chatClient;
    private final OrderLookupTool orderLookupTool;

    public OrderSupportAgentService(
            ChatClient.Builder chatClientBuilder,
            OrderLookupTool orderLookupTool
    ) {
        this.chatClient = chatClientBuilder.build();
        this.orderLookupTool = orderLookupTool;
    }

    public String answer(String message) {

        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("Message must not be blank.");
        }

        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .tools(orderLookupTool)
                .call()
                .content();

        if (!StringUtils.hasText(answer)) {

            throw new IllegalStateException(
                    "The AI model returned an empty response."
            );
        }

        return answer;
    }

}
