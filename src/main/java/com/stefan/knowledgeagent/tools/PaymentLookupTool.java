package com.stefan.knowledgeagent.tools;

import com.stefan.knowledgeagent.common.exception.ResourceNotFoundException;
import com.stefan.knowledgeagent.observability.AgentObservability;
import com.stefan.knowledgeagent.order.OrderRepository;
import com.stefan.knowledgeagent.payment.Payment;
import com.stefan.knowledgeagent.payment.PaymentService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PaymentLookupTool {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;
    private final AgentObservability observability;

    @Autowired
    public PaymentLookupTool(
            PaymentService paymentService,
            OrderRepository orderRepository,
            AgentObservability observability
    ) {

        this.paymentService = paymentService;
        this.orderRepository = orderRepository;
        this.observability = observability != null
                ? observability
                : AgentObservability.noop();
    }

    public PaymentLookupTool(
            PaymentService paymentService,
            OrderRepository orderRepository
    ) {
        this(
                paymentService,
                orderRepository,
                AgentObservability.noop()
        );
    }

    @Tool(
            name = "lookup_payment_by_order",
            description = """
                    Look up payment details for an order using its internal numeric
                    order ID. Returns payment status, amount, currency, refunded
                    amount and capture timestamp. Read-only.
                    """
    )
    public String lookupPaymentByOrder(
            @ToolParam(description = "The internal numeric ID of the order")
            long orderId
    ) {

        return observability.recordToolCall(
                "lookup_payment_by_order",
                () -> lookupPaymentByOrderInternal(orderId)
        );

    }

    private String lookupPaymentByOrderInternal(long orderId) {

        if (orderId <= 0) {
            return "INVALID_ORDER_ID: The order ID must be positive.";
        }

        if (!orderRepository.existsById(orderId)) {
            return "ORDER_NOT_FOUND: No order exists with ID "
                    + orderId + ".";
        }

        final Payment payment;

        try {

            payment = paymentService.getPaymentForOrder(orderId);

        } catch (ResourceNotFoundException exception) {

            if (!"PAYMENT_NOT_FOUND".equals(exception.getCode())) {
                throw exception;
            }

            return "PAYMENT_NOT_FOUND: Order " + orderId
                    + " exists, but no payment is recorded for it.";
        }

        return """
                Payment found.
                Order ID: %d
                Payment ID: %d
                Status: %s
                Amount: %s %s
                Amount refunded: %s %s
                Captured at: %s
                """.formatted(
                orderId,
                payment.getId(),
                payment.getStatus().name(),
                payment.getAmount().toPlainString(),
                payment.getCurrency(),
                payment.getAmountRefunded().toPlainString(),
                payment.getCurrency(),
                payment.getCapturedAt()
        );
    }
}
