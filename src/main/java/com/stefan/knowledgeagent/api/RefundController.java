package com.stefan.knowledgeagent.api;

import com.stefan.knowledgeagent.api.dto.CreateRefundRequest;
import com.stefan.knowledgeagent.api.dto.RefundResponse;
import com.stefan.knowledgeagent.payment.Refund;
import com.stefan.knowledgeagent.payment.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/payments/{paymentId}/refunds")
    public ResponseEntity<RefundResponse> requestRefund(
            @PathVariable Long paymentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRefundRequest request) {

        Refund refund = refundService.requestRefund(
                paymentId,
                request.amount(),
                idempotencyKey,
                request.reason()
        );

        return ResponseEntity
                .created(URI.create("/api/v1/refunds/" + refund.getId()))
                .body(RefundResponse.from(refund));
    }

    @GetMapping("/refunds/{refundId}")
    public RefundResponse getRefund(
            @PathVariable Long refundId) {

        return RefundResponse.from(refundService.getRefund(refundId));
    }
}
