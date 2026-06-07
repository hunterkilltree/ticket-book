package com.ticketbooking.booking.controller;

import com.ticketbooking.booking.dto.ReleaseRequest;
import com.ticketbooking.booking.dto.ReservationResponse;
import com.ticketbooking.booking.dto.ReserveRequest;
import com.ticketbooking.booking.dto.SeatResponse;
import com.ticketbooking.booking.service.SeatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final SeatService seatService;

    @GetMapping("/events/{eventId}/seats")
    public List<SeatResponse> seats(@PathVariable UUID eventId) {
        return seatService.listSeats(eventId);
    }

    @PostMapping("/reserve")
    public ReservationResponse reserve(@Valid @RequestBody ReserveRequest req) {
        return seatService.reserve(req);
    }

    @PostMapping("/release")
    public void release(@Valid @RequestBody ReleaseRequest req) {
        seatService.release(req.seatIds());
    }
}
