package com.ticketbooking.event.dto;

import java.util.UUID;

public record VenueResponse(UUID id, String name, String address, int capacity) {}
