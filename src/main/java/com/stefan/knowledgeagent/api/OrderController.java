package com.stefan.knowledgeagent.api;

import com.stefan.knowledgeagent.api.dto.CreateOrderRequest;
import com.stefan.knowledgeagent.api.dto.OrderResponse;
import com.stefan.knowledgeagent.order.Order;
import com.stefan.knowledgeagent.order.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        List<OrderService.CreateOrderItem> items =
                request.items().stream()
                        .map(item -> new OrderService.CreateOrderItem(
                                item.productId(),
                                item.productName(),
                                item.quantity(),
                                item.unitPrice()
                        ))
                        .toList();

        Order order = orderService.createOrder(
                request.customerId(),
                items,
                request.currency()
        );

        return ResponseEntity
                .created(URI.create("/api/v1/orders/" + order.getId()))
                .body(OrderResponse.from(order));
    }

    @GetMapping("/orders/{orderId}")
    public OrderResponse getOrder(@PathVariable Long orderId) {
        return OrderResponse.from(orderService.getOrder(orderId));
    }

    @GetMapping("/customers/{customerId}/orders")
    public List<OrderResponse> getOrdersForCustomer(
            @PathVariable Long customerId) {

        return orderService.getOrdersForCustomer(customerId)
                .stream()
                .map(OrderResponse::from)
                .toList();
    }

    @PostMapping("/orders/{orderId}/processing")
    public ResponseEntity<Void> startProcessing(
            @PathVariable Long orderId) {

        orderService.startProcessing(orderId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/orders/{orderId}/cancellation")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable Long orderId) {

        orderService.cancelOrder(orderId);

        return ResponseEntity.noContent().build();
    }

}
