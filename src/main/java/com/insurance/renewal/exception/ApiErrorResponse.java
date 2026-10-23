package com.insurance.renewal.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;

/**
 * Common JSON error body returned for EVERY failure, so Postman / Angular
 * always receive the same structure.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {
    @Builder.Default
    private final String timestamp = Instant.now().toString();
    private final int status;          // 409
    private final String error;        // "Conflict"
    private final String message;      // human readable reason
    private final String path;         // /api/claims
    private final Map<String, String> fieldErrors; // validation errors (field -> message)
}
