package com.ticketbooking.order.service;

import com.ticketbooking.order.dto.CreateOrderRequest;
import com.ticketbooking.order.dto.OrderResponse;
import com.ticketbooking.order.entity.Order;
import com.ticketbooking.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @InjectMocks OrderService orderService;

    @Test
    void create_computes_total_at_50_per_seat() {
        UUID userId = UUID.randomUUID();
        when(orderRepository.findByIdempotencyKey("k")).thenReturn(Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse res = orderService.create(
                new CreateOrderRequest(userId, List.of(UUID.randomUUID(), UUID.randomUUID())), "k");

        assertEquals(0, res.totalAmount().compareTo(new BigDecimal("100.00")));
        assertEquals("PENDING", res.status());
    }

    @Test
    void create_is_idempotent_on_key() {
        Order existing = new Order();
        existing.setUserId(UUID.randomUUID());
        existing.setTotalAmount(new BigDecimal("100.00"));
        when(orderRepository.findByIdempotencyKey("k")).thenReturn(Optional.of(existing));

        OrderResponse res = orderService.create(
                new CreateOrderRequest(existing.getUserId(), List.of(UUID.randomUUID())), "k");

        assertEquals(0, res.totalAmount().compareTo(new BigDecimal("100.00")));
        verify(orderRepository, never()).save(any());
    }
}
