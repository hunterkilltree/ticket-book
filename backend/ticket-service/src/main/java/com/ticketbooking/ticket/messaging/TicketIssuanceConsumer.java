package com.ticketbooking.ticket.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.messaging.PaymentCompletedPayload;
import com.ticketbooking.common.messaging.Topics;
import com.ticketbooking.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketIssuanceConsumer {

    private final TicketService ticketService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = Topics.PAYMENT_COMPLETED, groupId = "ticket-service")
    public void onPaymentCompleted(String message) {
        PaymentCompletedPayload payload = parse(message);
        for (UUID seatId : payload.seatIds()) {
            ticketService.issue(payload.orderId(), payload.userId(), seatId);
        }
        forwardTicketIssued(message);
        log.info("Issued {} ticket(s) for order {}", payload.seatIds().size(), payload.orderId());
    }

    private void forwardTicketIssued(String payloadJson) {
        // Re-broadcast so notification-service (email) can react later.
        kafkaTemplate.send(Topics.TICKET_ISSUED, payloadJson);
    }

    private PaymentCompletedPayload parse(String message) {
        try {
            return objectMapper.readValue(message, PaymentCompletedPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("Bad payment_completed payload: " + message, e);
        }
    }
}
