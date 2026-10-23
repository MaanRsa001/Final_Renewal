package com.insurance.renewal.broker.security;

/** The authenticated broker behind an API key. Every broker query is scoped to {@code brokerCode}. */
public record BrokerPrincipal(String brokerCode, String name) { }
