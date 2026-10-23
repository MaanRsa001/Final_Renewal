package com.insurance.renewal.auth.token;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 403 JSON when a logged-in user lacks the required role. */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException ex) throws IOException {
		JwtAuthenticationEntryPoint.writeJson(request, response, HttpServletResponse.SC_FORBIDDEN, "Forbidden",
				"You do not have permission to access this resource.");
	}
}
