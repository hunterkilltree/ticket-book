package com.ticketbooking.order.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(UUID userId, @NotEmpty List<UUID> seatIds) {}
