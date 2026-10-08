package com.technova.campusdesk.exception;

/** A request that is well formed but violates a business rule. Maps to HTTP 409. */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
