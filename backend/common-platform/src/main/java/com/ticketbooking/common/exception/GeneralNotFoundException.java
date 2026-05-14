package com.ticketbooking.common.exception;

public class GeneralNotFoundException extends RuntimeException {

    public GeneralNotFoundException(String message) {
        super(message);
    }

    public GeneralNotFoundException(String resource, Object id) {
        super(resource + " not found: " + id);
    }
}
