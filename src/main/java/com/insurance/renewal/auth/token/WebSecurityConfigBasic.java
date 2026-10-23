package com.insurance.renewal.auth.token;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;
 

/*@Configuration
@EnableWebSecurity
@Order(999*/
public class WebSecurityConfigBasic {

	@Autowired
	private BasicAuthenticationPoint basicAuthenticationPoint;
	@Autowired
	@Qualifier(value = "corsConfigurationSource")
	private CorsConfigurationSource corsConfig;
	 @Bean
	 public SecurityFilterChain filterChain(HttpSecurity http)throws Exception {

		 return http.csrf(AbstractHttpConfigurer::disable)
				 .cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(corsConfig))
				 .authorizeHttpRequests(auth -> auth.requestMatchers("/embedded/create/schedule/**","/embedded/create/policy/schedule/**",
						 "/v3/api-docs/**",
			              "/swagger-ui/**",
			              "/swagger-ui.html")
						 .permitAll()
						 .requestMatchers("/api/generatesequence","/api/updatebycustrefno","/basicauth/**","/embedded/create/**","post/notification/ack/mail")
						 .hasRole("USER")
						 ).httpBasic(b-> b.authenticationEntryPoint(basicAuthenticationPoint))
				 .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).build();


	 }
	@Autowired
	private BCryptPasswordEncoder bCryptPasswordEncoder;
	
	@Autowired
	public void configure(AuthenticationManagerBuilder auth) throws Exception {
		//PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
		auth.inMemoryAuthentication().withUser("ewayapi").password(bCryptPasswordEncoder.encode("ewayapi123#")).roles("USER");
	}
	  
/*
	@Bean
	public static NoOpPasswordEncoder bpasswordEncoder() {
		return (NoOpPasswordEncoder) NoOpPasswordEncoder.getInstance();
	}*/
	/*@Bean
    public BCryptPasswordEncoder basicpasswordEncoder(){
    	BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        return encoder;
    }*/
}
