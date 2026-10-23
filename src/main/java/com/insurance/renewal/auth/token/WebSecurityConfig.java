package com.insurance.renewal.auth.token;

import java.util.List;
import jakarta.servlet.DispatcherType;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.channel.ChannelProcessingFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Order(1000)
public class WebSecurityConfig {

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private JwtAuthenticationEntryPoint unauthorizedHandler;

    @Autowired
    private BasicAuthenticationPoint basicAuthenticationPoint;

    @Value("${ussd.auth.username}")
    private String ussdAuthUserName;

    @Value("${ussd.auth.password}")
    private String ussdAuthPwd;

    @Bean
    public AuthenticationManager authenticationManagerBean(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Autowired
    public void globalUserDetails(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService)
              .passwordEncoder(bCryptPasswordEncoder);
    }

    @Bean
    public JwtAuthenticationFilter authenticationTokenFilterBean() throws Exception {
        return new JwtAuthenticationFilter();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return (web) -> web.ignoring()
                .requestMatchers("/", "/resources/**", "/styles/**", "/static/**", "/jasper/**", "/public/**", "/webui/**", "/h2-console/**"
                        , "/configuration/**", "/swagger-ui/**", "/swagger-resources/**", "/api-docs", "/api-docs/**", "/fonts/**", "/v3/api-docs/**", "/v3/api-docs.yaml", "/v3/api-docs.yaml/**"
                        , "/*.html", "/*.jpg")
                .requestMatchers(
                        new AntPathRequestMatcher("/**/*.html"),
                        new AntPathRequestMatcher("/**/*.css"),
                        new AntPathRequestMatcher("/**/*.js"),
                        new AntPathRequestMatcher("/**/*.png"),
                        new AntPathRequestMatcher("/**/*.jpg"),
                        new AntPathRequestMatcher("/**/*.gif"),
                        new AntPathRequestMatcher("/**/*.svg"),
                        new AntPathRequestMatcher("/**/*.ico"),
                        new AntPathRequestMatcher("/**/*.ttf"),
                        new AntPathRequestMatcher("/**/*.woff"),
                        new AntPathRequestMatcher("/**/*.otf")
                );
    }

    private static final String[] NOAUTH_MATCHER = { "/authentication/login", "/api/policydata**",
            "/authentication/tokenregenrate", "/authentication/logout", "/api/changepassword", "/api/forgotpassword",
            "/authentication/byipaddress", "/basicauth/**", "/embedded/create/**", "/post/notification/ack/mail",
            "/authentication/doauth", "/selcom/v1/checkout/webhook", "/post/notification/pushnotification",
            "/claim/get/**", "/claim/get/policydetails", "/claim//product/{companyId}", "/claim/get/coverdetails",
            "/claim/viewQuoteDetails", "crm/authentication/validateToken", "/selcom/v1/checkout/cybersourceWebhook",
            "/pdf/view/Certificate/**", "api/yara/document/**", "/actuator/**", "/v3/api-docs/**", "/v3/api-docs.yaml", "/v3/api-docs.yaml/**", "/swagger-ui/**",
            "/swagger-ui.html", "/realpay/webhook/callback", "/selcom/v1/checkout/lipilawebhook", "/otp/sendNotification",
            "/admin/createregister", "/master/noauth/regionstate", "/master/noauth/region", "/otp/sendSmsNotification", "/otp/validate",
            "/policy/verify/**", "/selcom/oman/payment-return", "/error" };

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        corsConfiguration.setAllowedOrigins(List.of("*"));
        corsConfiguration.setAllowedMethods(List.of("*"));
        corsConfiguration.setAllowedHeaders(List.of("*"));
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain jwtAuthFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp ->
                                csp.policyDirectives("frame-ancestors 'self'")
                        )
                        .frameOptions(frame -> frame.sameOrigin())
                )
                .authorizeHttpRequests(auth ->
                        auth.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                                .requestMatchers(NOAUTH_MATCHER)
                                .permitAll()
                                .anyRequest().authenticated()
                )
                .formLogin(
                        formLogin -> formLogin.loginPage("/admin/region/list")
                                .loginPage("/authentication/login").permitAll()
                )
                .exceptionHandling(exceptionHandling ->
                        exceptionHandling.authenticationEntryPoint(unauthorizedHandler)
                                .accessDeniedHandler(new JsonAccessDeniedHandler())
                )
                .sessionManagement(sessionManagement ->
                        sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                );
        http
                .addFilterBefore(authenticationTokenFilterBean(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new CORSFilter(), ChannelProcessingFilter.class);

        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain basicAuthFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher((HttpServletRequest request) -> {
            return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION)).map(h -> {
                return h.toLowerCase().startsWith("basic ");
            }).orElse(false);
        }).httpBasic(Customizer.withDefaults())
                .userDetailsService(basicAuthUser())
                .csrf(AbstractHttpConfigurer::disable)
                .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/embedded/create/schedule/**", "/embedded/create/policy/schedule/**")
                        .permitAll()
                        .requestMatchers("/api/generatesequence", "/api/updatebycustrefno", "/basicauth/**", "/embedded/create/**", "post/notification/ack/mail")
                        .hasRole("USER")
                        .requestMatchers("/api/ussd").hasRole("USSD_USER")
                        .requestMatchers("/api/yara").hasRole("YARA_USER")
                ).httpBasic(b -> b.authenticationEntryPoint(basicAuthenticationPoint))
                .formLogin(
                        formLogin -> formLogin.loginPage("/admin/region/list")
                                .loginPage("/authentication/login").permitAll()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).build();
    }

    @Bean
    public UserDetailsService basicAuthUser() throws Exception {
        UserDetails user = User.builder()
                .username("ewayapi")
                .password(bCryptPasswordEncoder.encode("ewayapi123#"))
                .roles("USER")
                .build();

        UserDetails ussdUser = User.builder()
                .username(ussdAuthUserName)
                .password(bCryptPasswordEncoder.encode(ussdAuthPwd))
                .roles("USSD_USER")
                .build();

        return new InMemoryUserDetailsManager(user, ussdUser);
    }

}