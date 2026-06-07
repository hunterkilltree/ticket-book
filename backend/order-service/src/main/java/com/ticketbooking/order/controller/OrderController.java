package com.ticketbooking.order.controller;

import com.ticketbooking.order.dto.CreateOrderRequest;
import com.ticketbooking.order.dto.OrderResponse;
import com.ticketbooking.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String key = (idempotencyKey != null && !idempotencyKey.isBlank())
                ? idempotencyKey : UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(req, key));
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return orderService.get(id);
    }
}
