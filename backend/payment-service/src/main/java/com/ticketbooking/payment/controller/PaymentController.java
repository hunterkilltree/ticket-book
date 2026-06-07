package com.ticketbooking.payment.controller;

import com.ticketbooking.payment.dto.PaymentRequest;
import com.ticketbooking.payment.dto.PaymentResponse;
import com.ticketbooking.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<PaymentResponse> pay(
            @Valid @RequestBody PaymentRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String key = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey : UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.pay(req, key));
    }
}
