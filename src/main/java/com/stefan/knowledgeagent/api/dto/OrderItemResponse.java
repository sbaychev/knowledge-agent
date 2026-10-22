package com.stefan.knowledgeagent.api.dto;

import com.stefan.knowledgeagent.order.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        String productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
) {

    public static OrderItemResponse from(OrderItem item) {

        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getUnitPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity()))
        );
    }

}
