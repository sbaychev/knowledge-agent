package com.stefan.knowledgeagent.customer;

import com.stefan.knowledgeagent.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public Customer getCustomer(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                        "CUSTOMER_NOT_FOUND",
                        "Customer " + id + " does not exist"
                    ));
    }

    public Customer getByExternalId(String externalId) {
        return repository.findByExternalId(externalId)
                .orElseThrow(() ->
                    new ResourceNotFoundException(
                        "CUSTOMER_NOT_FOUND",
                        "Customer " + externalId + " does not exist"
                    ));
    }
}
