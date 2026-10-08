package com.technova.campusdesk.exception;

/** Invalid input that bean validation cannot detect on its own. Maps to HTTP 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
