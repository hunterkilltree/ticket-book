package com.ticketbooking.order.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResponse(UUID id, UUID userId, BigDecimal totalAmount, String status) {}
