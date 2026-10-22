package com.stefan.knowledgeagent.payment;

import com.stefan.knowledgeagent.common.exception.ResourceNotFoundException;
import com.stefan.knowledgeagent.order.Order;
import com.stefan.knowledgeagent.order.OrderRepository;
import com.stefan.knowledgeagent.order.OrderStatus;
import com.stefan.knowledgeagent.payment.exception.InvalidPaymentStateException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public PaymentService(PaymentRepository paymentRepository, OrderRepository orderRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Payment recordCapturedPayment(
            Long orderId,
            String providerReference) {

        Order order = this.orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "ORDER_NOT_FOUND",
                                "Order " + orderId + " does not exist"
                        ));

        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            throw new InvalidPaymentStateException(
                    "Order " + orderId + " already has a payment"
            );
        }

        if (order.getStatus() != OrderStatus.CREATED
                && order.getStatus() != OrderStatus.PROCESSING) {
            throw new InvalidPaymentStateException(
                    "Cannot record payment for order in status "
                            + order.getStatus()
            );
        }

        BigDecimal amount = order.getTotalAmount();

        if (amount == null || amount.signum() <= 0) {
            throw new InvalidPaymentStateException(
                    "Cannot record a payment for an order with no payable amount"
            );
        }

        Payment payment = new Payment(
                order,
                amount,
                order.getCurrency(),
                providerReference
        );

        return this.paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Payment getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "PAYMENT_NOT_FOUND",
                                "Payment " + paymentId + " does not exist"
                        ));
    }

    @Transactional(readOnly = true)
    public Payment getPaymentForOrder(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "PAYMENT_NOT_FOUND",
                                "No payment exists for order " + orderId
                        ));
    }

}
