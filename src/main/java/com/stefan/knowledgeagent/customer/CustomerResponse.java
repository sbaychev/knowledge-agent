package com.stefan.knowledgeagent.customer;

public record CustomerResponse(
    Long id,
    String externalId,
    String name,
    String email,
    CustomerStatus status
) {}
