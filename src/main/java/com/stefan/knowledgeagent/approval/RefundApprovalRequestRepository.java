
package com.stefan.knowledgeagent.approval;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefundApprovalRequestRepository extends JpaRepository<RefundApprovalRequest, UUID> {

    Optional<RefundApprovalRequest> findByRequesterSubjectAndIdempotencyKey(
            String requesterSubject,
            String idempotencyKey
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select approval
            from RefundApprovalRequest approval
            where approval.id = :id
            """)
    Optional<RefundApprovalRequest> findLockedById(
            @Param("id") UUID id
    );

    Page<RefundApprovalRequest> findByStatusAndExpiresAtAfterOrderByCreatedAtAsc(
            RefundApprovalStatus status,
            Instant now,
            Pageable pageable
    );
}
