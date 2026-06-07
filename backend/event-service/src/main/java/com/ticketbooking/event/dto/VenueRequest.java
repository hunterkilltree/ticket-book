package com.ticketbooking.event.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record VenueRequest(
        @NotBlank String name,
        @NotBlank String address,
        @Positive int capacity) {}
