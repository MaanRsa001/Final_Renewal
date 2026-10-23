package com.insurance.renewal.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 definition for the Renewal Backend.
 *   Spec (JSON) : /v3/api-docs
 *   Spec (YAML) : /v3/api-docs.yaml
 *   Swagger UI  : /swagger-ui.html
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI renewalOpenAPI(@Value("${app.openapi.server-url:/}") String serverUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("Policy Renewal Automation API")
                        .description("REST API for policy renewal processing, underwriter actions, rules/criteria, "
                                + "notifications and payments.")
                        .version("1.0.0")
                        .contact(new Contact().name("Renewal Backend Team")))
                .servers(List.of(new Server().url(serverUrl).description("Default server")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("JWT token. Paste the token only (without 'Bearer ').")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
