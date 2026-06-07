package com.ticketbooking.event.dto;

import com.ticketbooking.event.entity.EventStatus;
import java.time.Instant;
import java.util.UUID;

/** Partial update — only non-null fields are applied. */
public record UpdateEventRequest(
        String title,
        String artist,
        Instant startsAt,
        EventStatus status,
        UUID venueId) {}
