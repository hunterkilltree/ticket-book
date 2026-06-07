package com.ticketbooking.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ReserveRequest(
        @NotNull UUID eventId,
        @NotEmpty List<UUID> seatIds,
        UUID userId) {}
