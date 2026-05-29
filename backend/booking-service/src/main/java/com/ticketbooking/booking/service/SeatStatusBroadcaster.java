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
        // Cast payload to Object so the compiler picks convertAndSend(destination, payload)
        // rather than the ambiguous convertAndSend(payload, headers) overload.
        messagingTemplate.convertAndSend(
                "/topic/events/" + eventId + "/seats",
                (Object) Map.of("seatId", seatId, "state", state)
        );
    }
}
