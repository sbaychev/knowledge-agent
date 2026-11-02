package com.stefan.knowledgeagent.agent.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentExecutionEventRepository extends JpaRepository<AgentExecutionEvent, Long> {

    List<AgentExecutionEvent> findByExecutionIdOrderBySequenceNumberAsc(
            UUID executionId
    );
}
