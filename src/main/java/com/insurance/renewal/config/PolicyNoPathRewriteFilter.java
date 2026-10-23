package com.insurance.renewal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Policy numbers contain slashes (e.g. P11/2025/101/99999/99999/4000001), so a request like
 *   GET /api/policies/P11/2025/101/99999/99999/4000001/360-view
 * can never match "/{policyNo}/360-view" ({policyNo} = exactly one path segment).
 *
 * This filter runs before Spring Security / MVC and rewrites such URLs to
 *   GET /api/policies/360-view/P11/2025/101/99999/99999/4000001
 * which PolicyController handles with "/360-view/{*policyNo}".
 * Single-segment policy numbers (e.g. poc001-234) are left untouched.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PolicyNoPathRewriteFilter extends OncePerRequestFilter {

    // group(1) = policy number containing at least one '/'
    private static final Pattern LEGACY_360 =
            Pattern.compile("^/api/policies/(?!360-view/)([^?]+/[^?]+)/360-view/?$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String ctx = request.getContextPath() == null ? "" : request.getContextPath();
        String uri = request.getRequestURI();
        String path = uri.startsWith(ctx) ? uri.substring(ctx.length()) : uri;

        Matcher m = LEGACY_360.matcher(path);
        if (m.matches()) {
            String newPath = "/api/policies/360-view/" + m.group(1);
            chain.doFilter(new RewrittenRequest(request, ctx, newPath), response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static final class RewrittenRequest extends HttpServletRequestWrapper {
        private final String newUri;
        private final String newServletPath;

        RewrittenRequest(HttpServletRequest req, String ctx, String newPath) {
            super(req);
            this.newUri = ctx + newPath;
            this.newServletPath = newPath;
        }

        @Override public String getRequestURI() { return newUri; }

        @Override public StringBuffer getRequestURL() {
            HttpServletRequest r = (HttpServletRequest) getRequest();
            StringBuffer url = new StringBuffer();
            url.append(r.getScheme()).append("://").append(r.getServerName());
            int port = r.getServerPort();
            if (!(("http".equals(r.getScheme()) && port == 80) || ("https".equals(r.getScheme()) && port == 443))) {
                url.append(':').append(port);
            }
            return url.append(newUri);
        }

        @Override public String getServletPath() { return newServletPath; }

        @Override public String getPathInfo() { return null; }
    }
}
