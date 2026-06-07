package com.ticketbooking.order.service;

import com.ticketbooking.common.exception.GeneralNotFoundException;
import com.ticketbooking.order.dto.CreateOrderRequest;
import com.ticketbooking.order.dto.OrderResponse;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    /** Flat demo price per seat (seats carry no price yet). */
    private static final BigDecimal SEAT_PRICE = new BigDecimal("50.00");

    private final OrderRepository orderRepository;

    /** Idempotent: replaying the same Idempotency-Key returns the original order. */
    @Transactional
    public OrderResponse create(CreateOrderRequest req, String idempotencyKey) {
        return orderRepository.findByIdempotencyKey(idempotencyKey)
                .map(OrderService::toResponse)
                .orElseGet(() -> {
                    Order order = new Order();
                    order.setUserId(req.userId() != null ? req.userId() : UUID.randomUUID());
                    order.setIdempotencyKey(idempotencyKey);
                    order.setTotalAmount(SEAT_PRICE.multiply(BigDecimal.valueOf(req.seatIds().size())));
                    return toResponse(orderRepository.save(order));
                });
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return orderRepository.findById(id)
                .map(OrderService::toResponse)
                .orElseThrow(() -> new GeneralNotFoundException("Order", id));
    }

    private static OrderResponse toResponse(Order o) {
        return new OrderResponse(o.getId(), o.getUserId(), o.getTotalAmount(), o.getStatus().name());
    }
}
