package com.ticketbooking.common.messaging;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
public class EventEnvelope<T> {

    private String eventId = UUID.randomUUID().toString();
    private String eventType;
    private Instant occurredAt = Instant.now();
    private String correlationId;
    private T payload;

    public static <T> EventEnvelope<T> of(String eventType, T payload) {
        EventEnvelope<T> envelope = new EventEnvelope<>();
        envelope.setEventType(eventType);
        envelope.setPayload(payload);
        return envelope;
    }
}
