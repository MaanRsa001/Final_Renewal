package com.insurance.renewal.broker.config;

import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps Swagger exactly as it was (single spec at /v3/api-docs, no extra groups or dropdown entries).
 * It only registers the X-API-Key security scheme so the broker endpoints can reference it.
 * A standalone copy of the broker spec for sharing is in docs/openapi/broker-api-v1.yaml.
 */
@Configuration
public class BrokerOpenApiConfig {

    public static final String API_KEY = "brokerApiKey";

    @Bean
    public OpenApiCustomizer brokerApiKeySchemeCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) openApi.components(new io.swagger.v3.oas.models.Components());
            openApi.getComponents().addSecuritySchemes(API_KEY,
                    new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                            .in(SecurityScheme.In.HEADER).name("X-API-Key")
                            .description("API key issued to your brokerage."));
        };
    }
}
