package com.ticketbooking.event.dto;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id,
        String title,
        String artist,
        Instant startsAt,
        String status,
        VenueResponse venue) {}
