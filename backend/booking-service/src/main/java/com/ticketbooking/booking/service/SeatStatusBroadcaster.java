package com.ticketbooking.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SeatStatusBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastAvailable(UUID eventId, UUID seatId) {
        broadcast(eventId, seatId, "AVAILABLE");
    }

    public void broadcastReserved(UUID eventId, UUID seatId) {
        broadcast(eventId, seatId, "RESERVED");
    }

    public void broadcastSold(UUID eventId, UUID seatId) {
        broadcast(eventId, seatId, "SOLD");
    }

    private void broadcast(UUID eventId, UUID seatId, String state) {
        messagingTemplate.convertAndSend(
                "/topic/events/" + eventId + "/seats",
                Map.of("seatId", seatId, "state", state)
        );
    }
}
