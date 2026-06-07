package com.ticketbooking.payment.dto;

import java.util.UUID;

public record PaymentResponse(UUID id, UUID orderId, String status) {}
