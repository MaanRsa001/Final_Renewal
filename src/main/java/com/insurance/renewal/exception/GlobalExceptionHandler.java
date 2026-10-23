package com.insurance.renewal.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts every exception into the correct HTTP status + JSON body.
 * Without this, any exception thrown inside a controller is forwarded by Tomcat
 * to /error, which Spring Security protects -> Postman shows a blank 401.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // MySQL: Duplicate entry 'CLM-2026-0001' for key 'claims.claim_no'
    private static final Pattern DUPLICATE =
            Pattern.compile("Duplicate entry '(.*?)' for key '(?:.*\\.)?(.*?)'", Pattern.DOTALL);
    // MySQL: Column 'claim_no' cannot be null
    private static final Pattern NOT_NULL = Pattern.compile("Column '(.*?)' cannot be null");
    // MySQL: Field 'x' doesn't have a default value
    private static final Pattern NO_DEFAULT = Pattern.compile("Field '(.*?)' doesn't have a default value");
    // MySQL: Data too long for column 'x'
    private static final Pattern TOO_LONG = Pattern.compile("Data too long for column '(.*?)'");

    // ---------- Database errors ----------
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        String root = rootMessage(ex);
        log.warn("Data integrity violation on {}: {}", req.getRequestURI(), root);

        Matcher m = DUPLICATE.matcher(root);
        if (m.find()) {
            return build(HttpStatus.CONFLICT,
                    "Duplicate entry: '" + m.group(1) + "' already exists for " + toCamel(m.group(2)) + ".", req);
        }
        m = NOT_NULL.matcher(root);
        if (m.find()) {
            return build(HttpStatus.BAD_REQUEST, toCamel(m.group(1)) + " is required and cannot be null.", req);
        }
        m = NO_DEFAULT.matcher(root);
        if (m.find()) {
            return build(HttpStatus.BAD_REQUEST, toCamel(m.group(1)) + " is required.", req);
        }
        m = TOO_LONG.matcher(root);
        if (m.find()) {
            return build(HttpStatus.BAD_REQUEST, toCamel(m.group(1)) + " value is too long.", req);
        }
        if (root.contains("foreign key constraint")) {
            return build(HttpStatus.CONFLICT,
                    "Invalid reference: the related record does not exist, or this record is still used by other records.", req);
        }
        return build(HttpStatus.CONFLICT, "Data integrity violation: " + root, req);
    }

    // ---------- Validation / bad request ----------
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> fields.put(v.getPropertyPath().toString(), v.getMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        log.warn("Unreadable request body on {}: {}", req.getRequestURI(), rootMessage(ex));
        return build(HttpStatus.BAD_REQUEST,
                "Malformed JSON or invalid value in request body (check date format yyyy-MM-dd, numbers and enum values).", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST,
                "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'.", req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Required parameter '" + ex.getParameterName() + "' is missing.", req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    // ---------- Not found / method / media type ----------
    @ExceptionHandler({EntityNotFoundException.class, NoSuchElementException.class})
    public ResponseEntity<ApiErrorResponse> handleNotFound(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage() != null ? ex.getMessage() : "Record not found.", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "No endpoint found for " + req.getMethod() + " " + req.getRequestURI(), req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), req);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), req);
    }

    // ---------- Your own ResponseStatusException (409 / 400 ... already used in services) ----------
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        return build(status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR, ex.getReason(), req);
    }

    // ---------- Security (only real auth problems should be 401 / 403) ----------
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.", req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Authentication failed: " + ex.getMessage(), req);
    }

    // ---------- Anything else ----------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleAll(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected server error: " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()), req);
    }

    // ---------- helpers ----------
    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, HttpServletRequest req) {
        return build(status, message, req, null);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String message, HttpServletRequest req,
                                                   Map<String, String> fieldErrors) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(req.getRequestURI())
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.status(status).body(body);
    }

    private String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        return r.getMessage() != null ? r.getMessage() : t.getMessage();
    }

    /** claim_no -> claimNo */
    private String toCamel(String col) {
        if (col == null) return "value";
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : col.toCharArray()) {
            if (c == '_') { up = true; continue; }
            sb.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return sb.toString();
    }
}
