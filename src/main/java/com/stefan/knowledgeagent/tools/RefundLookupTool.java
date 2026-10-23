package com.stefan.knowledgeagent.tools;

import com.stefan.knowledgeagent.common.exception.ResourceNotFoundException;
import com.stefan.knowledgeagent.observability.AgentObservability;
import com.stefan.knowledgeagent.order.OrderRepository;
import com.stefan.knowledgeagent.payment.Payment;
import com.stefan.knowledgeagent.payment.PaymentService;
import com.stefan.knowledgeagent.payment.Refund;
import com.stefan.knowledgeagent.payment.RefundRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RefundLookupTool {

    private final PaymentService paymentService;
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final AgentObservability observability;

    @Autowired
    public RefundLookupTool(
            PaymentService paymentService,
            RefundRepository refundRepository,
            OrderRepository orderRepository,
            AgentObservability observability
    ) {

        this.paymentService = paymentService;
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.observability = observability != null
                ? observability
                : AgentObservability.noop();
    }

    public RefundLookupTool(
            PaymentService paymentService,
            RefundRepository refundRepository,
            OrderRepository orderRepository
    ) {

        this(
                paymentService,
                refundRepository,
                orderRepository,
                AgentObservability.noop()
        );
    }

    @Tool(
            name = "lookup_refunds_by_order",
            description = """
                    Look up the latest refund records for an order using its internal
                    numeric order ID. Returns refund IDs, statuses, amounts, currencies,
                    reasons and lifecycle timestamps. At most ten records. Read-only.
                    """
    )
    public String lookupRefundsByOrder(
            @ToolParam(description = "The internal numeric ID of the order")
            long orderId
    ) {

        return observability.recordToolCall(
                "lookup_refunds_by_order",
                () -> lookupRefundsByOrderInternal(orderId)
        );

    }

    private String lookupRefundsByOrderInternal(long orderId) {


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

            return "NO_REFUNDS_FOUND: Order " + orderId
                    + " has no payment record.";
        }

        List<Refund> refunds = refundRepository
                .findTop10ByPaymentIdOrderByCreatedAtDesc(payment.getId());

        if (refunds.isEmpty()) {

            return "NO_REFUNDS_FOUND: No refund records exist for order ID "
                    + orderId + ".";
        }

        String refundDetails = refunds.stream()
                .map(refund -> """
                        Refund ID: %d
                        Status: %s
                        Amount: %s %s
                        Reason: %s
                        Requested at: %s
                        Completed at: %s
                        Failed at: %s
                        """.formatted(
                        refund.getId(),
                        refund.getStatus().name(),
                        refund.getAmount().toPlainString(),
                        refund.getCurrency(),
                        refund.getReason(),
                        refund.getCreatedAt(),
                        refund.getCompletedAt() == null
                                ? "Not completed" : refund.getCompletedAt(),
                        refund.getFailedAt() == null
                                ? "Not failed" : refund.getFailedAt()
                ))
                .collect(Collectors.joining("\n"));

        return """
                Refund history found.
                Order ID: %d
                Payment ID: %d
                
                %s
                """.formatted(
                orderId,
                payment.getId(),
                refundDetails
        );
    }
}
