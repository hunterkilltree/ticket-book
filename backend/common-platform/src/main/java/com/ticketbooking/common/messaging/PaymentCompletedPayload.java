package com.ticketbooking.common.messaging;

import java.util.List;
import java.util.UUID;

/** Payload published on {@code payment_completed}; consumed by booking + ticket services. */
public record PaymentCompletedPayload(UUID orderId, UUID userId, UUID eventId, List<UUID> seatIds) {}
