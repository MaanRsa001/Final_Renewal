package com.insurance.renewal.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * Reads X-User-Role from the request header and enforces @RequiresRole on
 * controller methods. Demo-grade auth only - see RequiresRole javadoc.
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequiresRole requiresRole = handlerMethod.getMethodAnnotation(RequiresRole.class);
        if (requiresRole == null) {
            return true; // no restriction on this endpoint
        }
        String userRole = request.getHeader("X-User-Role");
        if (userRole == null || Arrays.stream(requiresRole.value()).noneMatch(r -> r.equalsIgnoreCase(userRole))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Forbidden - requires role: " + Arrays.toString(requiresRole.value()) + "\"}");
            return false;
        }
        return true;
    }
}
