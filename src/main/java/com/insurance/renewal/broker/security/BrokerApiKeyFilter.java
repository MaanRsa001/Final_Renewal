package com.insurance.renewal.broker.security;

import com.insurance.renewal.broker.config.BrokerApiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Authenticates /api/broker/** requests by the {@code X-API-Key} header.
 * Keys are compared as SHA-256 digests in constant time. A missing/unknown key gets a JSON 401.
 */
public class BrokerApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";

    private final Map<byte[], BrokerPrincipal> clientsByKeyDigest = new LinkedHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    public BrokerApiKeyFilter(BrokerApiProperties props) {
        for (BrokerApiProperties.Client c : props.getClients()) {
            if (c.getApiKey() == null || c.getApiKey().isBlank()
                    || c.getBrokerCode() == null || c.getBrokerCode().isBlank()) {
                continue; // disabled client
            }
            clientsByKeyDigest.put(sha256(c.getApiKey().trim()), new BrokerPrincipal(c.getBrokerCode().trim(), c.getName()));
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String key = req.getHeader(HEADER);
        BrokerPrincipal principal = key == null || key.isBlank() ? null : lookup(key.trim());
        if (principal == null) {
            reject(req, res);
            return;
        }
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_BROKER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(req, res);
    }

    private BrokerPrincipal lookup(String key) {
        byte[] digest = sha256(key);
        BrokerPrincipal found = null;
        for (Map.Entry<byte[], BrokerPrincipal> e : clientsByKeyDigest.entrySet()) {
            if (MessageDigest.isEqual(e.getKey(), digest)) found = e.getValue(); // no early exit
        }
        return found;
    }

    private void reject(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setStatus(HttpStatus.UNAUTHORIZED.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", 401);
        body.put("error", "Unauthorized");
        body.put("message", "Missing or invalid " + HEADER + " header.");
        body.put("path", req.getRequestURI());
        mapper.writeValue(res.getOutputStream(), body);
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
