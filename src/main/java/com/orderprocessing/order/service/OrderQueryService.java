package com.orderprocessing.order.service;

import com.orderprocessing.order.domain.Order;
import com.orderprocessing.order.domain.OrderStatus;
import com.orderprocessing.order.dto.OrderResponse;
import com.orderprocessing.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderQueryService {

    private final OrderRepository orderRepository;

    public OrderQueryService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public OrderResponse getOrderById(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderCommandService.OrderNotFoundException(orderId));
        return OrderResponse.fromEntity(order);
    }

    public List<OrderResponse> getOrdersByCustomerId(String customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    public List<OrderResponse> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByStatus(status)
                .stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    public List<OrderResponse> getActiveOrdersByCustomerId(String customerId) {
        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.PENDING,
                OrderStatus.INVENTORY_RESERVED,
                OrderStatus.PAYMENT_PROCESSING
        );

        return orderRepository.findByCustomerIdAndStatusIn(customerId, activeStatuses)
                .stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    public OrderSummary getCustomerOrderSummary(String customerId) {
        long pendingCount = orderRepository.countByCustomerIdAndStatus(customerId, OrderStatus.PENDING);
        long completedCount = orderRepository.countByCustomerIdAndStatus(customerId, OrderStatus.COMPLETED);
        long cancelledCount = orderRepository.countByCustomerIdAndStatus(customerId, OrderStatus.CANCELLED);

        List<OrderResponse> recentOrders = orderRepository
                .findByCustomerIdOrderByCreatedAtDesc(customerId)
                .stream()
                .limit(5)
                .map(OrderResponse::fromEntity)
                .toList();

        return new OrderSummary(pendingCount, completedCount, cancelledCount, recentOrders);
    }

    public record OrderSummary(
            long pendingOrders,
            long completedOrders,
            long cancelledOrders,
            List<OrderResponse> recentOrders
    ) {}
}