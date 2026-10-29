package com.stefan.knowledgeagent.mcp;

import com.stefan.knowledgeagent.tools.OrderLookupTool;
import com.stefan.knowledgeagent.tools.PaymentLookupTool;
import com.stefan.knowledgeagent.tools.RefundLookupTool;
import com.stefan.knowledgeagent.tools.ShipmentLookupTool;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderSupportMcpToolsTest {

    private final OrderLookupTool orderLookup =
            mock(OrderLookupTool.class);

    private final ShipmentLookupTool shipmentLookup =
            mock(ShipmentLookupTool.class);

    private final PaymentLookupTool paymentLookup =
            mock(PaymentLookupTool.class);

    private final RefundLookupTool refundLookup =
            mock(RefundLookupTool.class);

    private final OrderSupportMcpTools mcpTools =
            new OrderSupportMcpTools(
                    orderLookup,
                    shipmentLookup,
                    paymentLookup,
                    refundLookup
            );

    @Test
    void delegatesOrderLookupToExistingTool() {

        when(orderLookup.lookupOrder(1L))
                .thenReturn("Order found. Order ID: 1");

        assertThat(mcpTools.lookupOrder(1L))
                .isEqualTo("Order found. Order ID: 1");

        verify(orderLookup).lookupOrder(1L);
    }

    @Test
    void delegatesShipmentLookupToExistingTool() {

        when(shipmentLookup.lookupShipmentByOrder(1L))
                .thenReturn("Shipment found.");

        assertThat(mcpTools.lookupShipmentByOrder(1L))
                .isEqualTo("Shipment found.");

        verify(shipmentLookup).lookupShipmentByOrder(1L);
    }

    @Test
    void delegatesPaymentLookupToExistingTool() {

        when(paymentLookup.lookupPaymentByOrder(1L))
                .thenReturn("Payment found.");

        assertThat(mcpTools.lookupPaymentByOrder(1L))
                .isEqualTo("Payment found.");

        verify(paymentLookup).lookupPaymentByOrder(1L);
    }

    @Test
    void delegatesRefundLookupToExistingTool() {

        when(refundLookup.lookupRefundsByOrder(1L))
                .thenReturn("No refund records.");

        assertThat(mcpTools.lookupRefundsByOrder(1L))
                .isEqualTo("No refund records.");

        verify(refundLookup).lookupRefundsByOrder(1L);
    }
}
