package com.insurance.renewal.broker.controller;

import com.insurance.renewal.broker.dto.*;
import com.insurance.renewal.broker.security.BrokerPrincipal;
import com.insurance.renewal.broker.service.BrokerPolicyService;
import com.insurance.renewal.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/broker/v1")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "brokerApiKey")
@Tag(name = "Broker Policies", description = "Policy lookup, renewals and policy submission for brokers")
@ApiResponse(responseCode = "401", description = "Missing or invalid X-API-Key",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public class BrokerPolicyController {

    private final BrokerPolicyService service;

    @GetMapping("/policies")
    @Operation(operationId = "getPolicy", summary = "Get a policy",
            description = "Policy numbers contain slashes, so the number is passed as a query parameter.")
    @ApiResponse(responseCode = "200", description = "Policy found")
    @ApiResponse(responseCode = "404", description = "Policy does not exist or is not yours",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public BrokerPolicyDTO getPolicy(@AuthenticationPrincipal BrokerPrincipal broker,
                                     @RequestParam @NotBlank String policyNo) {
        return service.getPolicy(broker, policyNo);
    }

    @GetMapping("/policies/renewal-quote")
    @Operation(operationId = "getRenewalQuote", summary = "Get the latest renewal quote for a policy")
    @ApiResponse(responseCode = "200", description = "Renewal quote")
    @ApiResponse(responseCode = "404", description = "Policy not found, or no quote generated yet",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public BrokerRenewalQuoteDTO getRenewalQuote(@AuthenticationPrincipal BrokerPrincipal broker,
                                                 @RequestParam @NotBlank String policyNo) {
        return service.getRenewalQuote(broker, policyNo);
    }

    @GetMapping("/renewals-due")
    @Operation(operationId = "listRenewalsDue", summary = "List your active policies expiring in a date range",
            description = "Max range 366 days, max page size 100. Sorted by expiry date.")
    @ApiResponse(responseCode = "200", description = "Page of renewals due")
    @ApiResponse(responseCode = "400", description = "Invalid date range",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public BrokerPageDTO<BrokerRenewalDueDTO> renewalsDue(
            @AuthenticationPrincipal BrokerPrincipal broker,
            @Parameter(description = "Inclusive start, yyyy-MM-dd", example = "2026-10-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "Inclusive end, yyyy-MM-dd", example = "2026-10-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.renewalsDue(broker, fromDate, toDate, page, size);
    }

    @PostMapping("/policies")
    @Operation(operationId = "createPolicy", summary = "Submit a policy",
            description = "The policy is attached to your brokerage automatically; brokerCode is not accepted in the body.")
    @ApiResponse(responseCode = "201", description = "Policy created")
    @ApiResponse(responseCode = "400", description = "Validation failed",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Policy number already exists",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<BrokerPolicyDTO> createPolicy(@AuthenticationPrincipal BrokerPrincipal broker,
                                                        @Valid @RequestBody BrokerPolicyCreateRequest request) {
        BrokerPolicyDTO created = service.createPolicy(broker, request);
        return ResponseEntity.created(URI.create("/api/broker/v1/policies")).body(created);
    }
}
