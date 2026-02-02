package com.orderprocessing.order.repository;

import com.orderprocessing.order.domain.Order;
import com.orderprocessing.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);

    List<Order> findByStatus(OrderStatus status);

    Optional<Order> findBySagaId(String sagaId);

    @Query("SELECT o FROM Order o WHERE o.status = :status AND o.createdAt < :threshold")
    List<Order> findStaleOrdersByStatus(
            @Param("status") OrderStatus status,
            @Param("threshold") Instant threshold
    );

    @Query("SELECT o FROM Order o WHERE o.customerId = :customerId AND o.status IN :statuses")
    List<Order> findByCustomerIdAndStatusIn(
            @Param("customerId") String customerId,
            @Param("statuses") List<OrderStatus> statuses
    );

    @Query("SELECT COUNT(o) FROM Order o WHERE o.customerId = :customerId AND o.status = :status")
    long countByCustomerIdAndStatus(
            @Param("customerId") String customerId,
            @Param("status") OrderStatus status
    );
}