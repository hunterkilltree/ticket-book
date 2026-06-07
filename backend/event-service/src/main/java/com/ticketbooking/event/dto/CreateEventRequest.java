package com.ticketbooking.event.dto;

import com.ticketbooking.event.entity.EventStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateEventRequest(
        @NotBlank String title,
        @NotBlank String artist,
        @NotNull Instant startsAt,
        @NotNull UUID venueId,
        EventStatus status) {}
