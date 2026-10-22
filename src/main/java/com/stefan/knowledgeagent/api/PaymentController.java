package com.stefan.knowledgeagent.api;

import com.stefan.knowledgeagent.api.dto.PaymentResponse;
import com.stefan.knowledgeagent.api.dto.RecordPaymentRequest;
import com.stefan.knowledgeagent.payment.Payment;
import com.stefan.knowledgeagent.payment.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // NOTE: Registers an already confirmed payment | Does not handle cards and does not confirm that the carrier/ supplier has the money
    @PostMapping("/orders/{orderId}/payment")
    public ResponseEntity<PaymentResponse> recordCapturedPayment(
            @PathVariable Long orderId,
            @Valid @RequestBody RecordPaymentRequest request) {

        Payment payment = paymentService.recordCapturedPayment(
                orderId,
                request.providerReference()
        );

        return ResponseEntity
                .created(URI.create("/api/v1/payments/" + payment.getId()))
                .body(PaymentResponse.from(payment));
    }

    @GetMapping("/payments/{paymentId}")
    public PaymentResponse getPayment(
            @PathVariable Long paymentId) {

        return PaymentResponse.from(
                paymentService.getPayment(paymentId)
        );
    }

    @GetMapping("/orders/{orderId}/payment")
    public PaymentResponse getPaymentForOrder(
            @PathVariable Long orderId) {

        return PaymentResponse.from(
                paymentService.getPaymentForOrder(orderId)
        );
    }
}
