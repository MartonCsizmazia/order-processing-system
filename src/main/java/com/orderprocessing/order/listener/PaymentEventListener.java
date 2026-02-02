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

import java.math.BigDecimal;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final OrderCommandService orderCommandService;

    public PaymentEventListener(OrderCommandService orderCommandService) {
        this.orderCommandService = orderCommandService;
    }

    @KafkaListener(
            topics = KafkaConfig.PAYMENT_EVENTS_TOPIC,
            groupId = "${spring.application.name}-payment-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentEvent(
            @Payload PaymentEvent event,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Received payment event: type={}, sagaId={}, partition={}, offset={}",
                event.eventType(), event.sagaId(), partition, offset);

        try {
            switch (event.eventType()) {
                case PAYMENT_COMPLETED -> handlePaymentCompleted(event);
                case PAYMENT_FAILED -> handlePaymentFailed(event);
                case PAYMENT_REFUNDED -> handlePaymentRefunded(event);
                default -> log.warn("Unknown payment event type: {}", event.eventType());
            }

            acknowledgment.acknowledge();
            log.debug("Successfully processed payment event for saga: {}", event.sagaId());

        } catch (Exception ex) {
            log.error("Error processing payment event for saga {}: {}",
                    event.sagaId(), ex.getMessage(), ex);
            throw ex;
        }
    }

    private void handlePaymentCompleted(PaymentEvent event) {
        log.info("Payment completed for saga: {}, transactionId: {}",
                event.sagaId(), event.transactionId());
        orderCommandService.handlePaymentCompleted(event.sagaId());
    }

    private void handlePaymentFailed(PaymentEvent event) {
        log.warn("Payment failed for saga: {}, reason: {}",
                event.sagaId(), event.reason());
        orderCommandService.handlePaymentFailed(event.sagaId(), event.reason());
    }

    private void handlePaymentRefunded(PaymentEvent event) {
        log.info("Payment refunded for saga: {} (compensation complete)", event.sagaId());
        // This confirms the payment was rolled back as part of compensation
    }

    // Event record for payment events
    public record PaymentEvent(
            String eventId,
            String sagaId,
            String orderId,
            PaymentEventType eventType,
            String transactionId,
            BigDecimal amount,
            String reason
    ) {}

    public enum PaymentEventType {
        PAYMENT_COMPLETED,
        PAYMENT_FAILED,
        PAYMENT_REFUNDED
    }
}