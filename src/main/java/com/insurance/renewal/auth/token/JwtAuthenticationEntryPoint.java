package com.insurance.renewal.auth.token;

import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Returns a JSON 401 body (instead of an empty response) when the token is
 * missing / invalid. Real 401 only - other errors are handled by GlobalExceptionHandler.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint, Serializable {

	private static final long serialVersionUID = 1L;
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		writeJson(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized",
				"Authentication required: token is missing, invalid or expired.");
	}

	/** Writes the common error JSON without using sendError (which would trigger /error). */
	public static void writeJson(HttpServletRequest request, HttpServletResponse response, int status,
			String error, String message) throws IOException {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("timestamp", Instant.now().toString());
		body.put("status", status);
		body.put("error", error);
		body.put("message", message);
		body.put("path", request.getRequestURI());
		response.setStatus(status);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(MAPPER.writeValueAsString(body));
	}
}
