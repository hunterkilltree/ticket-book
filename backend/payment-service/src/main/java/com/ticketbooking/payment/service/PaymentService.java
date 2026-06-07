package com.ticketbooking.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.messaging.PaymentCompletedPayload;
import com.ticketbooking.common.messaging.Topics;
import com.ticketbooking.payment.dto.PaymentRequest;
import com.ticketbooking.payment.dto.PaymentResponse;
import com.ticketbooking.payment.entity.Payment;
import com.ticketbooking.payment.entity.PaymentStatus;
import com.ticketbooking.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** Idempotent payment. Simulates the gateway, then emits a payment_completed event. */
    @Transactional
    public PaymentResponse pay(PaymentRequest req, String idempotencyKey) {
        Payment existing = paymentRepository.findByIdempotencyKey(idempotencyKey)
                .or(() -> paymentRepository.findByOrderId(req.orderId()))
                .orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }

        Payment payment = new Payment();
        payment.setOrderId(req.orderId());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setAmount(req.amount());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setGatewayTransactionId("demo-" + UUID.randomUUID());
        Payment saved = paymentRepository.save(payment);

        publishPaymentCompleted(req);
        log.info("Payment {} succeeded for order {} ({} seats)",
                saved.getId(), req.orderId(), req.seatIds().size());
        return toResponse(saved);
    }

    private void publishPaymentCompleted(PaymentRequest req) {
        PaymentCompletedPayload payload = new PaymentCompletedPayload(
                req.orderId(), req.userId(), req.eventId(), req.seatIds());
        try {
            kafkaTemplate.send(Topics.PAYMENT_COMPLETED, req.orderId().toString(),
                    objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to publish payment_completed", e);
        }
    }

    private static PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getOrderId(), p.getStatus().name());
    }
}
