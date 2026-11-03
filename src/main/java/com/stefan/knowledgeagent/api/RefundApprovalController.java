
package com.stefan.knowledgeagent.api;

import com.stefan.knowledgeagent.api.dto.CreateRefundApprovalRequest;
import com.stefan.knowledgeagent.api.dto.RefundApprovalResponse;
import com.stefan.knowledgeagent.api.dto.RejectRefundApprovalRequest;
import com.stefan.knowledgeagent.approval.RefundApprovalRequest;
import com.stefan.knowledgeagent.approval.RefundApprovalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/approvals")
public class RefundApprovalController {

    private final RefundApprovalService approvalService;

    public RefundApprovalController(RefundApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/refunds")
    @PreAuthorize("hasAnyRole('AGENT', 'APPROVER')")
    public ResponseEntity<RefundApprovalResponse> requestRefundApproval(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRefundApprovalRequest request,
            Authentication authentication
    ) {

        RefundApprovalRequest approval = approvalService.requestRefund(
                authentication.getName(),
                request.paymentId(),
                request.amount(),
                idempotencyKey,
                request.reason(),
                null
        );

        return ResponseEntity
                .created(URI.create("/api/v1/approvals/" + approval.getId()))
                .body(response(approval));
    }

    @GetMapping("/{approvalId}")
    @PreAuthorize("hasAnyRole('AGENT', 'APPROVER')")
    public RefundApprovalResponse getApproval(
            @PathVariable UUID approvalId,
            Authentication authentication
    ) {

        RefundApprovalRequest approval = approvalService.getForActor(
                approvalId,
                authentication.getName(),
                isApprover(authentication)
        );

        return response(approval);
    }

    @GetMapping
    @PreAuthorize("hasRole('APPROVER')")
    public Page<RefundApprovalResponse> listPendingApprovals(
            @PageableDefault(size = 25, sort = "createdAt") Pageable pageable
    ) {

        return approvalService.listPending(pageable)
                .map(approval -> RefundApprovalResponse.from(approval, List.of()));

    }

    @PostMapping("/{approvalId}/approve")
    @PreAuthorize("hasRole('APPROVER')")
    public RefundApprovalResponse approve(
            @PathVariable UUID approvalId,
            Authentication authentication
    ) {

        return response(approvalService.approve(
                approvalId,
                authentication.getName()
        ));

    }

    @PostMapping("/{approvalId}/reject")
    @PreAuthorize("hasRole('APPROVER')")
    public RefundApprovalResponse reject(
            @PathVariable UUID approvalId,
            @Valid @RequestBody RejectRefundApprovalRequest request,
            Authentication authentication
    ) {

        return response(approvalService.reject(
                approvalId,
                authentication.getName(),
                request.reason()
        ));

    }

    private RefundApprovalResponse response(RefundApprovalRequest approval) {

        return RefundApprovalResponse.from(
                approval,
                approvalService.getEvents(approval.getId())
        );

    }

    private static boolean isApprover(Authentication authentication) {

        return authentication.getAuthorities().stream()
                .anyMatch(authority ->
                        "ROLE_APPROVER".equals(authority.getAuthority())
                );

    }
}
