package com.stefan.knowledgeagent.order;

import com.stefan.knowledgeagent.common.exception.ResourceNotFoundException;
import com.stefan.knowledgeagent.customer.Customer;
import com.stefan.knowledgeagent.customer.CustomerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerService customerService;

    public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
    public static final String ORDER = "Order ";
    public static final String DOES_NOT_EXIST = " does not exist";

    public OrderService(
            OrderRepository orderRepository,
            CustomerService customerService) {

        this.orderRepository = orderRepository;
        this.customerService = customerService;
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long id) {

        return orderRepository.findDetailedById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                ORDER_NOT_FOUND,
                                ORDER + id + DOES_NOT_EXIST
                        ));
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersForCustomer(Long customerId) {

        return orderRepository
                .findByCustomerIdOrderByCreatedAtDesc(
                        customerId
                );
    }

    @Transactional
    public Order createOrder(
            Long customerId,
            List<CreateOrderItem> items,
            String currency) {

        Customer customer = customerService.getCustomer(customerId);

        Order order = new Order(customer, currency);

        for (CreateOrderItem item : items) {

            OrderItem orderItem =
                    new OrderItem(
                            item.productId(),
                            item.productName(),
                            item.quantity(),
                            item.unitPrice()
                    );

            order.addItem(orderItem);
        }

        return orderRepository.save(order);
    }

    @Transactional
    public void cancelOrder(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                ORDER_NOT_FOUND,
                                ORDER + orderId + DOES_NOT_EXIST
                        ));

        order.cancel();
    }

    @Transactional
    public void startProcessing(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                ORDER_NOT_FOUND,
                                ORDER + orderId + DOES_NOT_EXIST
                        ));

        order.startProcessing();
    }

    public record CreateOrderItem(

            String productId,
            String productName,
            int quantity,
            BigDecimal unitPrice

    ) {
    }
}
