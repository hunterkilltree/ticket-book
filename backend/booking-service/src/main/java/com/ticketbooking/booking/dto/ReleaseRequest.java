package com.ticketbooking.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;

public record ReleaseRequest(@NotEmpty List<UUID> seatIds) {}
