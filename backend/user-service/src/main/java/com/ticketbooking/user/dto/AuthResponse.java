package com.ticketbooking.user.dto;

public record AuthResponse(String token, UserResponse user) {}
