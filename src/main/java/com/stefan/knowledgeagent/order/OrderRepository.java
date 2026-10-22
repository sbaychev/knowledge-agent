package com.stefan.knowledgeagent.order;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("""
    select distinct o
    from Order o
    left join fetch o.items
    where o.id = :orderId
    """)
    Optional<Order> findDetailedById(@Param("orderId") Long orderId);

    @EntityGraph(attributePaths = "items")
    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

}
