package com.stefan.knowledgeagent.customer;

import com.stefan.knowledgeagent.RequestType;

public record CustomerRequest(
    RequestType type,
    Long customerId,
    Long orderId
) {}
