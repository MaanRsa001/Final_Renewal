package com.insurance.renewal.broker.config;

import com.insurance.renewal.broker.security.BrokerApiKeyFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Dedicated, highest-priority security chain for the broker API. It only matches /api/broker/**,
 * so the existing JWT and Basic-auth chains are untouched.
 */
@Configuration
@EnableConfigurationProperties(BrokerApiProperties.class)
public class BrokerSecurityConfig {

    @Bean
    @Order(0)
    public SecurityFilterChain brokerApiFilterChain(HttpSecurity http, BrokerApiProperties props) throws Exception {
        http.securityMatcher("/api/broker/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().hasRole("BROKER"))
                .addFilterBefore(new BrokerApiKeyFilter(props), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
