package com.orderprocessing.common.event;

import java.time.Instant;
import java.util.UUID;

public abstract class BaseEvent {

    private final String eventId;
    private final Instant timestamp;
    private final String aggregateId;
    private final String eventType;

    protected BaseEvent(String aggregateId, String eventType) {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.aggregateId = aggregateId;
        this.eventType = eventType;
    }

    // For deserialization
    protected BaseEvent() {
        this.eventId = null;
        this.timestamp = null;
        this.aggregateId = null;
        this.eventType = null;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }
}