package com.ticketbooking.booking.service;

import com.ticketbooking.booking.dto.ReservationResponse;
import com.ticketbooking.booking.dto.ReserveRequest;
import com.ticketbooking.booking.entity.Seat;
import com.ticketbooking.booking.entity.SeatState;
import com.ticketbooking.booking.repository.SeatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeatServiceTest {

    @Mock SeatRepository seatRepository;
    @Mock SeatLockService seatLockService;
    @Mock SeatStatusBroadcaster broadcaster;
    @InjectMocks SeatService seatService;

    private Seat availableSeat(UUID id, UUID eventId) {
        Seat s = new Seat();
        s.setId(id);
        s.setEventId(eventId);
        s.setState(SeatState.AVAILABLE);
        return s;
    }

    @Test
    void reserve_marks_seat_reserved_when_lock_acquired() {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Seat seat = availableSeat(seatId, eventId);
        when(seatRepository.findById(seatId)).thenReturn(Optional.of(seat));
        when(seatLockService.acquireLock(eq(seatId), any())).thenReturn(true);
        when(seatRepository.save(any(Seat.class))).thenAnswer(i -> i.getArgument(0));

        ReservationResponse res = seatService.reserve(new ReserveRequest(eventId, List.of(seatId), null));

        assertEquals(List.of(seatId), res.reservedSeatIds());
        assertTrue(res.failedSeatIds().isEmpty());
        assertEquals(SeatState.RESERVED, seat.getState());
        verify(broadcaster).broadcastReserved(eventId, seatId);
    }

    @Test
    void reserve_fails_when_lock_not_acquired() {
        UUID eventId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Seat seat = availableSeat(seatId, eventId);
        when(seatRepository.findById(seatId)).thenReturn(Optional.of(seat));
        when(seatLockService.acquireLock(eq(seatId), any())).thenReturn(false);

        ReservationResponse res = seatService.reserve(new ReserveRequest(eventId, List.of(seatId), null));

        assertEquals(List.of(seatId), res.failedSeatIds());
        assertTrue(res.reservedSeatIds().isEmpty());
        assertEquals(SeatState.AVAILABLE, seat.getState());
        verify(seatRepository, never()).save(any());
    }
}
