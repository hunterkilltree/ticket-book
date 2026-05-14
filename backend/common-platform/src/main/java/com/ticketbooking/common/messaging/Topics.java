package com.ticketbooking.common.messaging;

public final class Topics {

    public static final String SEAT_RESERVED     = "seat_reserved";
    public static final String PAYMENT_COMPLETED = "payment_completed";
    public static final String TICKET_ISSUED     = "ticket_issued";
    public static final String PAYMENT_FAILED    = "payment_failed";
    public static final String EVENT_PUBLISHED   = "event_published";
    public static final String ORDER_CREATED     = "order_created";

    private Topics() {}
}
