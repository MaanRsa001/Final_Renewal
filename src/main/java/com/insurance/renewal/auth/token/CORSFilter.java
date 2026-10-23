package com.insurance.renewal.auth.token;
import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CORSFilter implements Filter {

	/**
	 * This method checks on the validity of the session upon direct request. If the
	 * session is not valid, the user is forwarded to login page. When This method
	 * is done, execute the other filters in the chain.
	 *
	 * @param req   servlet request.
	 * @param res   servlet response.
	 * @param chain any other filters configured to run.
	 * @see javax.servlet.Filter#doFilter(javax.servlet.ServletRequest,
	 *      javax.servlet.ServletResponse, javax.servlet.FilterChain)
	 */
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
			throws IOException, ServletException {
		HttpServletRequest httpRequest = (HttpServletRequest) req;
		HttpServletResponse response = (HttpServletResponse) res;
		String origin = httpRequest.getHeader("Origin");
		response.setHeader("Access-Control-Allow-Origin", "*");
		response.setHeader("Access-Control-Allow-Methods", "*");
		response.setHeader("Access-Control-Allow-Headers","*");
//		response.setHeader("Access-Control-Allow-Origin", origin);
//		response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
//		response.setHeader("Access-Control-Allow-Headers","Origin, Authorization, Accept, Content-Type, X-Requested-With, X-CSRF-Token, X-Auth-Token");
		response.setHeader("X-Requested-With", "XMLHttpRequest");
		response.setHeader("Access-Control-Max-Age", "3600");
		if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
			response.setStatus(HttpServletResponse.SC_OK);
		} else {
			chain.doFilter(req, res);
		}
	}

	public void init(FilterConfig filterConfig) {
	}

	public void destroy() {
	}

}


