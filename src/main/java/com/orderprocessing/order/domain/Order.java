package com.orderprocessing.order.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Entity
@Table(name = "orders")
public class Order {

    // Getters
    @Id
    private String id;

    @Column(nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @Version
    private Long version;

    // Saga tracking
    @Column(name = "saga_id")
    private String sagaId;

    @Column(name = "failure_reason")
    private String failureReason;

    protected Order() {}

    public static Order create(String customerId, List<OrderItem> items) {
        Order order = new Order();
        order.id = UUID.randomUUID().toString();
        order.customerId = customerId;
        order.status = OrderStatus.PENDING;
        order.createdAt = Instant.now();
        order.sagaId = UUID.randomUUID().toString();

        items.forEach(order::addItem);
        order.recalculateTotal();

        return order;
    }

    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
        recalculateTotal();
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
        recalculateTotal();
    }

    private void recalculateTotal() {
        this.totalAmount = items.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void transitionTo(OrderStatus newStatus) {
        validateTransition(newStatus);
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    private void validateTransition(OrderStatus newStatus) {
        // State machine validation
        boolean valid = switch (this.status) {
            case PENDING -> newStatus == OrderStatus.INVENTORY_RESERVED
                    || newStatus == OrderStatus.INVENTORY_FAILED
                    || newStatus == OrderStatus.CANCELLED;
            case INVENTORY_RESERVED -> newStatus == OrderStatus.PAYMENT_PROCESSING
                    || newStatus == OrderStatus.COMPENSATING;
            case PAYMENT_PROCESSING -> newStatus == OrderStatus.PAYMENT_COMPLETED
                    || newStatus == OrderStatus.PAYMENT_FAILED;
            case PAYMENT_COMPLETED -> newStatus == OrderStatus.COMPLETED;
            case PAYMENT_FAILED, INVENTORY_FAILED -> newStatus == OrderStatus.COMPENSATING
                    || newStatus == OrderStatus.CANCELLED;
            case COMPENSATING -> newStatus == OrderStatus.CANCELLED;
            default -> false;
        };

        if (!valid) {
            throw new IllegalStateException(
                    String.format("Invalid transition from %s to %s", this.status, newStatus)
            );
        }
    }

    public void markFailed(String reason) {
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public List<OrderItem> getItems() { return new ArrayList<>(items); }
}
