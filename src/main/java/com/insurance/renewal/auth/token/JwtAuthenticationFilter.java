package com.insurance.renewal.auth.token;

import static com.insurance.renewal.auth.token.Constants.HEADER_STRING;
import static com.insurance.renewal.auth.token.Constants.TOKEN_PREFIX;

import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.insurance.renewal.auth.bean.SessionMaster;
import com.insurance.renewal.repository.SessionMasterRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;




public class JwtAuthenticationFilter extends OncePerRequestFilter {

	@Lazy
	@Autowired
	@Qualifier(value = "authendicationServiceImpl")
	UserDetailsService userDetailsService;

	@Autowired
	private SessionMasterRepository sessionRep;
	
	@Autowired
	private JwtTokenUtil jwtTokenUtil;
	
	private String userType ;
	private String subUserType ;
	
	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)	throws IOException, ServletException {

		String header = req.getHeader(HEADER_STRING);
		String username = null;
		String authToken = null;
		String loginId = null;
		SessionMaster table = new SessionMaster();
		HeaderMapRequestWrapper requestWrapper = new HeaderMapRequestWrapper(req);
		if (header != null && header.startsWith(TOKEN_PREFIX)) {
			authToken = header.replace(TOKEN_PREFIX, "");
			table = sessionRep.findByTempTokenid(authToken);
			if (table == null) {
				JwtAuthenticationEntryPoint.writeJson(req, res, 401, "Unauthorized", "Invalid or expired token. Please login again.");
				return;
			}
			
			loginId = table.getLoginId();
			authToken = table.getTokenId();
			userType = table.getUserType() ;
			subUserType = table.getSubUserType() ;
	        requestWrapper.addHeader(HEADER_STRING, authToken);
			try {
				username = jwtTokenUtil.getUsernameFromToken(authToken);
				Date today = new Date();
				if(today.before(table.getEndTime())  ) {
					table.setStartTime(today);
					Calendar cal = Calendar.getInstance();
					cal.add(Calendar.MINUTE, 50);
					Date endTime = cal.getTime();
					table.setEndTime(endTime );
					String token = jwtTokenUtil.doGenerateToken(table.getLoginId());
					table.setTokenId(token);
					table.setIpAddress(req.getLocalAddr() +":" +req.getLocalPort());
					sessionRep.save(table);
				}
			} catch (IllegalArgumentException e) {
				logger.error("an error occured during getting username from token", e);
			} catch (Exception e) {
				Date today = new Date();
				if(today.before(table.getEndTime())  ) {
					table.setStartTime(today);
					Calendar cal = Calendar.getInstance();
					cal.add(Calendar.MINUTE, 50);
					Date endTime = cal.getTime();
					table.setEndTime(endTime );
					String token = null;
					try {
						token = jwtTokenUtil.doGenerateToken(table.getLoginId());
					} catch (Exception e1) {
						e1.printStackTrace();
					}
					table.setTokenId(token);
					table.setIpAddress(req.getLocalAddr() +":" +req.getLocalPort());
					sessionRep.save(table);
					try {
						username = jwtTokenUtil.getUsernameFromToken(authToken);
					} catch (Exception e1) {
						e1.printStackTrace();
					}
				} else {
					logger.warn("the token is expired and not valid anymore");
					username = table.getTokenId();
				}
			} 
		} else {
			logger.warn("couldn't find bearer string, will ignore the header");
		}
		if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
			
			//Added for getting companyid
			String combinedUsername = username + ":" + loginId;
			
			UserDetails userDetails = null;
			try {
				userDetails = userDetailsService.loadUserByUsername(combinedUsername);
			} catch (Exception e) {
				JwtAuthenticationEntryPoint.writeJson(req, res, 401, "Unauthorized", "Invalid session or user not found. Please login again.");
				return;
			}
			boolean validtoken = false;;
			try {
				validtoken = jwtTokenUtil.validateToken(username, table.getTempTokenid(), userDetails,req);
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			if(validtoken && table.getStatus().equals("DE-ACTIVE")) {
				validtoken = false;
				res.setHeader(username, "This session has been terminated. User logged in to a new session");
			}
			
			if (validtoken) {
							
				if(userType.equalsIgnoreCase("Issuer") && (subUserType.equalsIgnoreCase("SuperAdmin") 
						|| subUserType.equalsIgnoreCase("surveyor") || subUserType.equalsIgnoreCase("claimofficer") 
						|| subUserType.equalsIgnoreCase("garage") || subUserType.equalsIgnoreCase("bidder"))) {
					UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
							userDetails, null, Arrays.asList(new SimpleGrantedAuthority("ROLE_ADMIN")));
					authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(requestWrapper));
					logger.info("authenticated user " + username + ", setting security context");
					SecurityContextHolder.getContext().setAuthentication(authentication);
					
				} else if(userType.equalsIgnoreCase("Issuer") && ( subUserType.equalsIgnoreCase("high") ||subUserType.equalsIgnoreCase("both") ) ) {
					UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
							userDetails, null, Arrays.asList(new SimpleGrantedAuthority("ROLE_APPROVER")));
					authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(requestWrapper));
					logger.info("authenticated user " + username + ", setting security context");
					SecurityContextHolder.getContext().setAuthentication(authentication);
					
				} else if (userType.equalsIgnoreCase("Broker") || userType.equalsIgnoreCase("User") || (userType.equalsIgnoreCase("Issuer") &&  subUserType.equalsIgnoreCase("low"))   ) {
					UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
							userDetails, null, Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
					authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(requestWrapper));
					logger.info("authenticated user " + username + ", setting security context");
					SecurityContextHolder.getContext().setAuthentication(authentication);
				}
				
				
			}
		}
		chain.doFilter(requestWrapper, res);
	}
}