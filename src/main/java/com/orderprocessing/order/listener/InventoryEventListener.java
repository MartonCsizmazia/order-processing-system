package com.orderprocessing.order.listener;

import com.orderprocessing.order.config.KafkaConfig;
import com.orderprocessing.order.service.OrderCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final OrderCommandService orderCommandService;

    public InventoryEventListener(OrderCommandService orderCommandService) {
        this.orderCommandService = orderCommandService;
    }

    @KafkaListener(
            topics = KafkaConfig.INVENTORY_EVENTS_TOPIC,
            groupId = "${spring.application.name}-inventory-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleInventoryEvent(
            @Payload InventoryEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received inventory event: type={}, sagaId={}, partition={}, offset={}",
                event.eventType(), event.sagaId(), partition, offset);

        try {
            switch (event.eventType()) {
                case INVENTORY_RESERVED -> handleInventoryReserved(event);
                case INVENTORY_RESERVATION_FAILED -> handleInventoryFailed(event);
                case INVENTORY_RELEASED -> handleInventoryReleased(event);
                default -> log.warn("Unknown inventory event type: {}", event.eventType());
            }

            acknowledgment.acknowledge();
            log.debug("Successfully processed inventory event for saga: {}", event.sagaId());

        } catch (Exception ex) {
            log.error("Error processing inventory event for saga {}: {}",
                    event.sagaId(), ex.getMessage(), ex);
            // Don't acknowledge - message will be retried based on error handler config
            throw ex;
        }
    }

    private void handleInventoryReserved(InventoryEvent event) {
        log.info("Inventory reserved for saga: {}", event.sagaId());
        orderCommandService.handleInventoryReserved(event.sagaId());
    }

    private void handleInventoryFailed(InventoryEvent event) {
        log.warn("Inventory reservation failed for saga: {}, reason: {}",
                event.sagaId(), event.reason());
        orderCommandService.handleInventoryFailed(event.sagaId(), event.reason());
    }

    private void handleInventoryReleased(InventoryEvent event) {
        log.info("Inventory released for saga: {} (compensation complete)", event.sagaId());
        // Order is already in COMPENSATING state, this confirms inventory rollback
    }

    // Event record for inventory events
    public record InventoryEvent(
            String eventId,
            String sagaId,
            String orderId,
            InventoryEventType eventType,
            String reason
    ) {}

    public enum InventoryEventType {
        INVENTORY_RESERVED,
        INVENTORY_RESERVATION_FAILED,
        INVENTORY_RELEASED
    }
}