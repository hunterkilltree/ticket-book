package com.ticketbooking.booking.service;

import com.ticketbooking.booking.dto.ReservationResponse;
import com.ticketbooking.booking.dto.ReserveRequest;
import com.ticketbooking.booking.dto.SeatResponse;
import com.ticketbooking.booking.entity.Seat;
import com.ticketbooking.booking.entity.SeatState;
import com.ticketbooking.booking.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatService {

    private final SeatRepository seatRepository;
    private final SeatLockService seatLockService;
    private final SeatStatusBroadcaster broadcaster;

    @Value("${booking.seat-lock.ttl-minutes:10}")
    private long ttlMinutes;

    /** List seats for an event; auto-generate a small demo grid the first time. */
    @Transactional
    public List<SeatResponse> listSeats(UUID eventId) {
        List<Seat> seats = seatRepository.findAllByEventId(eventId);
        if (seats.isEmpty()) {
            seats = generateGrid(eventId);
        }
        return seats.stream().map(SeatService::toResponse).toList();
    }

    private List<Seat> generateGrid(UUID eventId) {
        List<Seat> seats = new ArrayList<>();
        for (String section : List.of("A", "B")) {
            for (int row = 1; row <= 3; row++) {
                for (int num = 1; num <= 8; num++) {
                    Seat s = new Seat();
                    s.setEventId(eventId);
                    s.setSection(section);
                    s.setRow(String.valueOf(row));
                    s.setNumber(String.valueOf(num));
                    s.setState(SeatState.AVAILABLE);
                    seats.add(s);
                }
            }
        }
        return seatRepository.saveAll(seats);
    }

    /** Reserve seats: acquire the Redis lock (the real no-double-booking guard) then mark RESERVED. */
    @Transactional
    public ReservationResponse reserve(ReserveRequest req) {
        UUID userId = req.userId() != null ? req.userId() : UUID.randomUUID();
        List<UUID> reserved = new ArrayList<>();
        List<UUID> failed = new ArrayList<>();
        for (UUID seatId : req.seatIds()) {
            Seat seat = seatRepository.findById(seatId).orElse(null);
            if (seat == null || seat.getState() != SeatState.AVAILABLE) {
                failed.add(seatId);
                continue;
            }
            if (seatLockService.acquireLock(seatId, userId)) {
                seat.setState(SeatState.RESERVED);
                seatRepository.save(seat);
                broadcaster.broadcastReserved(seat.getEventId(), seatId);
                reserved.add(seatId);
            } else {
                failed.add(seatId);
            }
        }
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(ttlMinutes));
        return new ReservationResponse(reserved, failed, expiresAt);
    }

    /** Manually release reserved seats (e.g. user cancels). */
    @Transactional
    public void release(List<UUID> seatIds) {
        for (UUID seatId : seatIds) {
            seatRepository.findById(seatId).ifPresent(seat -> {
                if (seat.getState() == SeatState.RESERVED) {
                    seat.setState(SeatState.AVAILABLE);
                    seatRepository.save(seat);
                    seatLockService.releaseLock(seatId);
                    broadcaster.broadcastAvailable(seat.getEventId(), seatId);
                }
            });
        }
    }

    private static SeatResponse toResponse(Seat s) {
        return new SeatResponse(s.getId(), s.getEventId(), s.getSection(), s.getRow(),
                s.getNumber(), s.getState().name());
    }
}
