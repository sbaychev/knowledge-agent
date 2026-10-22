package com.stefan.knowledgeagent.customer;

import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
            customer.getId(),
            customer.getExternalId(),
            customer.getName(),
            customer.getEmail(),
            customer.getStatus()
        );
    }
}
