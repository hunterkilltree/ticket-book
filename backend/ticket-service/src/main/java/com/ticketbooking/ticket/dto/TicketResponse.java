package com.ticketbooking.ticket.dto;

import java.util.UUID;

public record TicketResponse(UUID id, UUID orderId, UUID seatId, String qrCode, String status) {}
