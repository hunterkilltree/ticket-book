package com.ticketbooking.booking.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        List<UUID> reservedSeatIds, List<UUID> failedSeatIds, Instant expiresAt) {}
