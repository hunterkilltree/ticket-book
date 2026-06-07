package com.ticketbooking.booking.dto;

import java.util.UUID;

public record SeatResponse(
        UUID id, UUID eventId, String section, String row, String number, String state) {}
