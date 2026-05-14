package com.ticketbooking.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatLockService {

    private static final String KEY_PREFIX = "seat:lock:";

    private final StringRedisTemplate redisTemplate;

    @Value("${booking.seat-lock.ttl-minutes:10}")
    private long ttlMinutes;

    /**
     * Acquires a distributed lock for the seat. Returns true only if this caller won the lock.
     * Uses SET NX EX (atomic) so only one concurrent reservation can win.
     */
    public boolean acquireLock(UUID seatId, UUID userId) {
        String key = KEY_PREFIX + seatId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, userId.toString(), Duration.ofMinutes(ttlMinutes));
        return Boolean.TRUE.equals(acquired);
    }

    public void releaseLock(UUID seatId) {
        redisTemplate.delete(KEY_PREFIX + seatId);
    }

    public boolean isLocked(UUID seatId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + seatId));
    }
}
