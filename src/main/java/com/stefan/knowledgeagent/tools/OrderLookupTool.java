package com.stefan.knowledgeagent.tools;

import com.stefan.knowledgeagent.observability.AgentObservability;
import com.stefan.knowledgeagent.order.Order;
import com.stefan.knowledgeagent.order.OrderRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OrderLookupTool {

    private final OrderRepository orderRepository;
    private final AgentObservability observability;

    @Autowired
    public OrderLookupTool(
            OrderRepository orderRepository,
            AgentObservability observability
    ) {

        this.orderRepository = orderRepository;
        this.observability = observability != null
                ? observability
                : AgentObservability.noop();
    }

    public OrderLookupTool(OrderRepository orderRepository) {
        this(orderRepository, AgentObservability.noop());
    }

    @Tool(
            name = "lookup_order",
            description = """
                    Look up an existing order by its internal numeric order ID.
                    Returns the order status, customer ID, total amount, currency,
                    and items. Use this tool when a user asks about a specific
                    order. Do not invent an order ID.
                    """
    )
    public String lookupOrder(@ToolParam(description = "The internal numeric ID of the order") long orderId) {

        return observability.recordToolCall(
                "lookup_order",
                () -> lookupOrderInternal(orderId)
        );

    }

    private String lookupOrderInternal(long orderId) {

        if (orderId <= 0) {
            return "INVALID_ORDER_ID: The order ID must be positive.";

        }

        return orderRepository.findDetailedById(orderId)
                .map(this::describeOrder)
                .orElse("ORDER_NOT_FOUND: No order exists with ID " + orderId + ".");
    }

    private String describeOrder(Order order) {

        String items = order.getItems().stream()
                .map(item -> """
                        - Product: %s
                          Product ID: %s
                          Quantity: %d
                          Unit price: %s %s
                        """.formatted(
                        item.getProductName(),
                        item.getProductId(),
                        item.getQuantity(),
                        item.getUnitPrice().toPlainString(),
                        order.getCurrency()
                ))
                .reduce("", String::concat);

        return """
                Order found.
                Order ID: %d
                Customer ID: %d
                Status: %s
                Total: %s %s
                Items:
                %s
                """.formatted(
                order.getId(),
                order.getCustomer().getId(),
                order.getStatus().name(),
                order.getTotalAmount().toPlainString(),
                order.getCurrency(),
                items.isBlank() ? "No items" : items
        );
    }

}
