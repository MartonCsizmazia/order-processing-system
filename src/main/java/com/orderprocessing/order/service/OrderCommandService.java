package com.orderprocessing.order.service;

import com.orderprocessing.order.config.KafkaConfig;
import com.orderprocessing.order.domain.Order;
import com.orderprocessing.order.domain.OrderItem;
import com.orderprocessing.order.domain.OrderStatus;
import com.orderprocessing.order.dto.CreateOrderRequest;
import com.orderprocessing.order.dto.OrderResponse;
import com.orderprocessing.order.event.OrderEvent;
import com.orderprocessing.order.event.OrderEventType;
import com.orderprocessing.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class OrderCommandService {

    private static final Logger log = LoggerFactory.getLogger(OrderCommandService.class);

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderCommandService(OrderRepository orderRepository,
                               KafkaTemplate<String, Object> kafkaTemplate) {
        this.orderRepository = orderRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for customer: {}", request.customerId());

        // Map request items to domain objects
        List<OrderItem> items = request.items().stream()
                .map(item -> new OrderItem(
                        item.productId(),
                        item.productName(),
                        item.quantity(),
                        item.unitPrice()
                ))
                .toList();

        // Create order aggregate
        Order order = Order.create(request.customerId(), items);

        // Persist order
        Order savedOrder = orderRepository.save(order);
        log.info("Order created with ID: {} and saga ID: {}", savedOrder.getId(), savedOrder.getSagaId());

        // Publish event to start saga
        publishOrderEvent(savedOrder, OrderEventType.ORDER_CREATED);

        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional
    public OrderResponse updateOrderStatus(String orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.transitionTo(newStatus);
        Order savedOrder = orderRepository.save(order);

        // Determine event type based on new status
        OrderEventType eventType = mapStatusToEventType(newStatus);
        publishOrderEvent(savedOrder, eventType);

        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional
    public OrderResponse handleInventoryReserved(String sagaId) {
        Order order = orderRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new SagaNotFoundException(sagaId));

        order.transitionTo(OrderStatus.INVENTORY_RESERVED);
        order.transitionTo(OrderStatus.PAYMENT_PROCESSING);

        Order savedOrder = orderRepository.save(order);
        publishOrderEvent(savedOrder, OrderEventType.ORDER_PAYMENT_PROCESSING);

        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional
    public OrderResponse handleInventoryFailed(String sagaId, String reason) {
        Order order = orderRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new SagaNotFoundException(sagaId));

        order.transitionTo(OrderStatus.INVENTORY_FAILED);
        order.markFailed(reason);
        order.transitionTo(OrderStatus.CANCELLED);

        Order savedOrder = orderRepository.save(order);
        publishOrderEvent(savedOrder, OrderEventType.ORDER_CANCELLED);

        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional
    public OrderResponse handlePaymentCompleted(String sagaId) {
        Order order = orderRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new SagaNotFoundException(sagaId));

        order.transitionTo(OrderStatus.PAYMENT_COMPLETED);
        order.transitionTo(OrderStatus.COMPLETED);

        Order savedOrder = orderRepository.save(order);
        publishOrderEvent(savedOrder, OrderEventType.ORDER_COMPLETED);

        log.info("Order {} completed successfully", savedOrder.getId());
        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional
    public OrderResponse handlePaymentFailed(String sagaId, String reason) {
        Order order = orderRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new SagaNotFoundException(sagaId));

        order.transitionTo(OrderStatus.PAYMENT_FAILED);
        order.markFailed(reason);
        order.transitionTo(OrderStatus.COMPENSATING);

        Order savedOrder = orderRepository.save(order);

        // Trigger compensation - release inventory
        publishOrderEvent(savedOrder, OrderEventType.ORDER_COMPENSATION_STARTED);

        return OrderResponse.fromEntity(savedOrder);
    }

    @Transactional
    public OrderResponse cancelOrder(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed order");
        }

        order.transitionTo(OrderStatus.CANCELLED);
        Order savedOrder = orderRepository.save(order);
        publishOrderEvent(savedOrder, OrderEventType.ORDER_CANCELLED);

        return OrderResponse.fromEntity(savedOrder);
    }

    private void publishOrderEvent(Order order, OrderEventType eventType) {
        List<OrderEvent.OrderItemPayload> itemPayloads = order.getItems().stream()
                .map(item -> new OrderEvent.OrderItemPayload(
                        item.getProductId(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice()
                ))
                .toList();

        OrderEvent event = new OrderEvent(
                order.getId(),
                eventType,
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                itemPayloads,
                order.getSagaId()
        );

        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                KafkaConfig.ORDER_EVENTS_TOPIC,
                order.getId(), // Use order ID as partition key for ordering
                event
        );

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event {} for order {}: {}",
                        eventType, order.getId(), ex.getMessage());
            } else {
                log.debug("Published event {} for order {} to partition {}",
                        eventType, order.getId(),
                        result.getRecordMetadata().partition());
            }
        });
    }

    private OrderEventType mapStatusToEventType(OrderStatus status) {
        return switch (status) {
            case INVENTORY_RESERVED -> OrderEventType.ORDER_INVENTORY_RESERVED;
            case INVENTORY_FAILED -> OrderEventType.ORDER_INVENTORY_FAILED;
            case PAYMENT_PROCESSING -> OrderEventType.ORDER_PAYMENT_PROCESSING;
            case PAYMENT_COMPLETED -> OrderEventType.ORDER_PAYMENT_COMPLETED;
            case PAYMENT_FAILED -> OrderEventType.ORDER_PAYMENT_FAILED;
            case COMPLETED -> OrderEventType.ORDER_COMPLETED;
            case CANCELLED -> OrderEventType.ORDER_CANCELLED;
            case COMPENSATING -> OrderEventType.ORDER_COMPENSATION_STARTED;
            default -> throw new IllegalArgumentException("No event type for status: " + status);
        };
    }

    // Custom exceptions
    public static class OrderNotFoundException extends RuntimeException {
        public OrderNotFoundException(String orderId) {
            super("Order not found: " + orderId);
        }
    }

    public static class SagaNotFoundException extends RuntimeException {
        public SagaNotFoundException(String sagaId) {
            super("Saga not found: " + sagaId);
        }
    }
}