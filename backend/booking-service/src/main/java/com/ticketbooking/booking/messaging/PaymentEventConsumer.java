package com.ticketbooking.booking.messaging;

import com.ticketbooking.booking.entity.Seat;
import com.ticketbooking.booking.entity.SeatState;
import com.ticketbooking.booking.repository.SeatRepository;
import com.ticketbooking.booking.service.SeatLockService;
import com.ticketbooking.booking.service.SeatStatusBroadcaster;
import com.ticketbooking.common.exception.GeneralNotFoundException;
import com.ticketbooking.common.messaging.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final SeatRepository seatRepository;
    private final SeatLockService seatLockService;
    private final SeatStatusBroadcaster broadcaster;

    @KafkaListener(topics = Topics.PAYMENT_COMPLETED, groupId = "booking-service")
    @Transactional
    public void onPaymentCompleted(String seatIdRaw) {
        UUID seatId = UUID.fromString(seatIdRaw);
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new GeneralNotFoundException("Seat", seatId));
        seat.setState(SeatState.SOLD);
        seatRepository.save(seat);
        seatLockService.releaseLock(seatId);
        broadcaster.broadcastSold(seat.getEventId(), seatId);
        log.info("Seat {} confirmed as SOLD", seatId);
    }
}
