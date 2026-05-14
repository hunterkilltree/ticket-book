package com.ticketbooking.booking.repository;

import com.ticketbooking.booking.entity.Seat;
import com.ticketbooking.booking.entity.SeatState;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
    List<Seat> findAllByEventId(UUID eventId);
    List<Seat> findAllByState(SeatState state);
    List<Seat> findAllByEventIdAndState(UUID eventId, SeatState state);
}
