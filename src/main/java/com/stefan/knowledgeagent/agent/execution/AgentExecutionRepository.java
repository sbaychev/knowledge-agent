package com.stefan.knowledgeagent.agent.execution;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AgentExecutionRepository extends JpaRepository<AgentExecution, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select execution
            from AgentExecution execution
            where execution.id = :id
            """)
    Optional<AgentExecution> findLockedById(
            @Param("id") UUID id
    );
}
