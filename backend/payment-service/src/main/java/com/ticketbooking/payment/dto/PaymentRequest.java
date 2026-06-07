package com.ticketbooking.payment.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record PaymentRequest(
        @NotNull UUID orderId,
        @NotEmpty List<UUID> seatIds,
        @NotNull @Positive BigDecimal amount,
        UUID eventId,
        UUID userId) {}
