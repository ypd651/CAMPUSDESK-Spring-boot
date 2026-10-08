package com.technova.campusdesk.exception;

import com.technova.campusdesk.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Centralized exception handling: every error leaves the API with the same JSON shape. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ApiError> notFound(ResourceNotFoundException e, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, e.getMessage(), request, null);
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ApiError> conflict(BusinessRuleException e, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, e.getMessage(), request, null);
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiError> badRequest(BadRequestException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, e.getMessage(), request, null);
    }

    @ExceptionHandler(UnauthorizedException.class)
    ResponseEntity<ApiError> unauthorized(UnauthorizedException e, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, e.getMessage(), request, null);
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    ResponseEntity<ApiError> forbidden(RuntimeException e, HttpServletRequest request) {
        String message = e instanceof ForbiddenException ? e.getMessage() : "Access denied";
        return build(HttpStatus.FORBIDDEN, message, request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> constraint(ConstraintViolationException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Validation failed", request, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed JSON or invalid value in the request body", request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + e.getName() + "'", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> integrity(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("Data integrity violation on {}", request.getRequestURI());
        return build(HttpStatus.CONFLICT, "The data conflicts with an existing record", request, null);
    }

    /** Last resort. Standard Spring MVC errors (404 route, 405...) keep their own status. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest request) {
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            HttpStatus resolved = HttpStatus.resolve(status.value());
            String reason = resolved != null ? resolved.getReasonPhrase() : "Request error";
            return build(status, reason, request, null);
        }
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected internal error", request, null);
    }

    private ResponseEntity<ApiError> build(HttpStatusCode status, String message, HttpServletRequest request,
                                           Map<String, String> fieldErrors) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String error = resolved != null ? resolved.getReasonPhrase() : "Error";
        ApiError body = new ApiError(Instant.now(), status.value(), error, message,
                request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
