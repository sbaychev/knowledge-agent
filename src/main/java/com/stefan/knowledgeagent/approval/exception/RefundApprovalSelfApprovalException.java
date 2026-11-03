package com.stefan.knowledgeagent.approval.exception;

public class RefundApprovalSelfApprovalException extends RuntimeException {

    public RefundApprovalSelfApprovalException() {
        super(
                "The requester cannot approve or reject their own refund request."
        );
    }
}
