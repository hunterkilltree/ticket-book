package com.ticketbooking.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.messaging.PaymentCompletedPayload;
import com.ticketbooking.common.messaging.Topics;
import com.ticketbooking.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketIssuedConsumer {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    /** ticket-service forwards the payment payload onto ticket_issued; we notify the buyer. */
    @KafkaListener(topics = Topics.TICKET_ISSUED, groupId = "notification-service")
    public void onTicketIssued(String message) {
        PaymentCompletedPayload payload = parse(message);
        // The payload carries userId (not email) for now — MailHog catches all addresses, so a
        // derived demo address is fine until a real user email is threaded through. See log backlog.
        String to = "user-" + payload.userId() + "@demo.local";
        String body = "Your booking is confirmed!\n\n"
                + "Order: " + payload.orderId() + "\n"
                + "Seats: " + payload.seatIds().size() + "\n\n"
                + "Show this email at the venue. Enjoy the show!";
        emailService.send(to, "Your tickets are confirmed", body);
        log.info("Notified buyer for order {}", payload.orderId());
    }

    private PaymentCompletedPayload parse(String message) {
        try {
            return objectMapper.readValue(message, PaymentCompletedPayload.class);
        } catch (Exception e) {
            throw new IllegalStateException("Bad ticket_issued payload: " + message, e);
        }
    }
}
