package com.technova.campusdesk.exception;

/** Missing or invalid credentials. Maps to HTTP 401. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
