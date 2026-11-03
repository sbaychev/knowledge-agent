package com.stefan.knowledgeagent.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RefundApprovalEventRepository extends JpaRepository<RefundApprovalEvent, Long> {

    List<RefundApprovalEvent> findByApprovalRequestIdOrderBySequenceNumberAsc(
            UUID approvalRequestId
    );
}
