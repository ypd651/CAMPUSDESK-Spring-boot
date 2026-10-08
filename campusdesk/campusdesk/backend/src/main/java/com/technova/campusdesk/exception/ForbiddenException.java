package com.technova.campusdesk.exception;

/** The authenticated user is not allowed to perform the operation. Maps to HTTP 403. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
