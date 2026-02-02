package com.orderprocessing.order.event;

import com.orderprocessing.common.event.BaseEvent;
import com.orderprocessing.order.domain.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public class OrderEvent extends BaseEvent {

    private String customerId;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private List<OrderItemPayload> items;
    private String sagaId;
    private String failureReason;

    // Default constructor for deserialization
    public OrderEvent() {
        super();
    }

    public OrderEvent(String orderId, OrderEventType eventType, String customerId,
                      OrderStatus status, BigDecimal totalAmount,
                      List<OrderItemPayload> items, String sagaId) {
        super(orderId, eventType.name());
        this.customerId = customerId;
        this.status = status;
        this.totalAmount = totalAmount;
        this.items = items;
        this.sagaId = sagaId;
    }

    // Static factory for failure events
    public static OrderEvent failure(String orderId, OrderEventType eventType,
                                     String sagaId, String reason) {
        OrderEvent event = new OrderEvent();
        // Using reflection or builder would be cleaner, but keeping it simple
        event.failureReason = reason;
        event.sagaId = sagaId;
        return new OrderEvent(orderId, eventType, null, null, null, null, sagaId) {
            @Override
            public String getFailureReason() {
                return reason;
            }
        };
    }

    // Nested class for item payload in events
    public static class OrderItemPayload {
        private String productId;
        private String productName;
        private Integer quantity;
        private BigDecimal unitPrice;

        public OrderItemPayload() {}

        public OrderItemPayload(String productId, String productName,
                                Integer quantity, BigDecimal unitPrice) {
            this.productId = productId;
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public String getProductId() { return productId; }
        public void setProductId(String productId) { this.productId = productId; }

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    }

    // Getters and setters
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public List<OrderItemPayload> getItems() { return items; }
    public void setItems(List<OrderItemPayload> items) { this.items = items; }

    public String getSagaId() { return sagaId; }
    public void setSagaId(String sagaId) { this.sagaId = sagaId; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}