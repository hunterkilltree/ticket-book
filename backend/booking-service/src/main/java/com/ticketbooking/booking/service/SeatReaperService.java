package com.ticketbooking.booking.service;

import com.ticketbooking.booking.entity.Seat;
import com.ticketbooking.booking.entity.SeatState;
import com.ticketbooking.booking.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Releases RESERVED seats whose Redis lock has expired (TTL elapsed without a payment_completed event).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatReaperService {

    private final SeatRepository seatRepository;
    private final SeatLockService seatLockService;
    private final SeatStatusBroadcaster broadcaster;

    @Scheduled(fixedDelayString = "${booking.reaper.interval-ms:30000}")
    @Transactional
    public void releaseExpiredReservations() {
        List<Seat> reserved = seatRepository.findAllByState(SeatState.RESERVED);
        for (Seat seat : reserved) {
            if (!seatLockService.isLocked(seat.getId())) {
                seat.setState(SeatState.AVAILABLE);
                seatRepository.save(seat);
                broadcaster.broadcastAvailable(seat.getEventId(), seat.getId());
                log.info("Reaped expired reservation for seat {}", seat.getId());
            }
        }
    }
}
