package com.ticketbooking.common.exception;

public class InvalidStateTransitionException extends RuntimeException {

    public InvalidStateTransitionException(String entity, String from, String to) {
        super("Cannot transition " + entity + " from " + from + " to " + to);
    }
}
