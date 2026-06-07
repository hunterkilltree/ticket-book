package com.ticketbooking.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbooking.common.messaging.Topics;
import com.ticketbooking.payment.dto.PaymentRequest;
import com.ticketbooking.payment.dto.PaymentResponse;
import com.ticketbooking.payment.entity.Payment;
import com.ticketbooking.payment.entity.PaymentStatus;
import com.ticketbooking.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    private PaymentRepository repository;
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    private PaymentService service;

    @BeforeEach
    void setUp() {
        repository = mock(PaymentRepository.class);
        service = new PaymentService(repository, kafka, new ObjectMapper());
    }

    @Test
    void pay_succeeds_and_emits_payment_completed() {
        UUID orderId = UUID.randomUUID();
        when(repository.findByIdempotencyKey("k")).thenReturn(Optional.empty());
        when(repository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(repository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponse res = service.pay(new PaymentRequest(
                orderId, List.of(UUID.randomUUID()), new BigDecimal("50.00"),
                UUID.randomUUID(), UUID.randomUUID()), "k");

        assertEquals("SUCCESS", res.status());
        verify(kafka).send(eq(Topics.PAYMENT_COMPLETED), eq(orderId.toString()), anyString());
    }

    @Test
    void pay_is_idempotent_and_does_not_reemit() {
        UUID orderId = UUID.randomUUID();
        Payment existing = new Payment();
        existing.setOrderId(orderId);
        existing.setStatus(PaymentStatus.SUCCESS);
        when(repository.findByIdempotencyKey("k")).thenReturn(Optional.of(existing));

        PaymentResponse res = service.pay(new PaymentRequest(
                orderId, List.of(UUID.randomUUID()), new BigDecimal("50.00"), null, null), "k");

        assertEquals("SUCCESS", res.status());
        verify(kafka, never()).send(anyString(), anyString(), anyString());
    }
}
