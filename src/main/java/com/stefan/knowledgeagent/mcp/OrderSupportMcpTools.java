package com.stefan.knowledgeagent.mcp;

import com.stefan.knowledgeagent.tools.OrderLookupTool;
import com.stefan.knowledgeagent.tools.PaymentLookupTool;
import com.stefan.knowledgeagent.tools.RefundLookupTool;
import com.stefan.knowledgeagent.tools.ShipmentLookupTool;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mcp-server")
public class OrderSupportMcpTools {

    private final OrderLookupTool orderLookupTool;
    private final ShipmentLookupTool shipmentLookupTool;
    private final PaymentLookupTool paymentLookupTool;
    private final RefundLookupTool refundLookupTool;

    public OrderSupportMcpTools(
            OrderLookupTool orderLookupTool,
            ShipmentLookupTool shipmentLookupTool,
            PaymentLookupTool paymentLookupTool,
            RefundLookupTool refundLookupTool
    ) {

        this.orderLookupTool = orderLookupTool;
        this.shipmentLookupTool = shipmentLookupTool;
        this.paymentLookupTool = paymentLookupTool;
        this.refundLookupTool = refundLookupTool;

    }

    @McpTool(
            name = "lookup_order",
            description = """
                    Look up an existing order by its internal numeric order ID.
                    Returns order status, customer ID, total, currency and items.
                    Read-only; use this tool to confirm whether an order exists.
                    """,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    openWorldHint = false
            )
    )
    public String lookupOrder(
            @McpToolParam(
                    description = "The internal numeric ID of the order",
                    required = true
            )
            long orderId
    ) {

        return orderLookupTool.lookupOrder(orderId);
    }

    @McpTool(
            name = "lookup_shipment_by_order",
            description = """
                    Look up shipment details by the internal numeric order ID.
                    Returns shipment status, carrier, tracking number and
                    shipment/delivery timestamps. Read-only.
                    """,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    openWorldHint = false
            )
    )
    public String lookupShipmentByOrder(
            @McpToolParam(
                    description = "The internal numeric ID of the order",
                    required = true
            )
            long orderId
    ) {

        return shipmentLookupTool.lookupShipmentByOrder(orderId);
    }

    @McpTool(
            name = "lookup_payment_by_order",
            description = """
                    Look up payment details by the internal numeric order ID.
                    Returns payment status, amount, currency, refunded amount
                    and capture timestamp. Read-only.
                    """,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    openWorldHint = false
            )
    )
    public String lookupPaymentByOrder(
            @McpToolParam(
                    description = "The internal numeric ID of the order",
                    required = true
            )
            long orderId
    ) {

        return paymentLookupTool.lookupPaymentByOrder(orderId);
    }

    @McpTool(
            name = "lookup_refunds_by_order",
            description = """
                    Look up the latest refund records for an order.
                    Returns refund IDs, statuses, amounts, reasons and timestamps.
                    The result contains at most ten records. Read-only.
                    """,
            annotations = @McpTool.McpAnnotations(
                    readOnlyHint = true,
                    destructiveHint = false,
                    openWorldHint = false
            )
    )
    public String lookupRefundsByOrder(
            @McpToolParam(
                    description = "The internal numeric ID of the order",
                    required = true
            )
            long orderId
    ) {

        return refundLookupTool.lookupRefundsByOrder(orderId);
    }
}
