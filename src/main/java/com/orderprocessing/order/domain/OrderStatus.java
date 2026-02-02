package com.orderprocessing.order.domain;

public enum OrderStatus {
    PENDING,
    INVENTORY_RESERVED,
    INVENTORY_FAILED,
    PAYMENT_PROCESSING,
    PAYMENT_COMPLETED,
    PAYMENT_FAILED,
    COMPLETED,
    CANCELLED,
    COMPENSATING
}