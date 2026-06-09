package com.leafy.searchservice.config;

import com.leafy.common.security.SecurityContextFilter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecurityConfig {

    SecurityContextFilter securityContextFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
	http
		.csrf(AbstractHttpConfigurer::disable)
		.cors(cors -> cors.configurationSource(corsConfigurationSource()))
		.authorizeHttpRequests(auth -> auth
			.requestMatchers("/actuator/**").permitAll()
			.requestMatchers("/v3/api-docs/**").permitAll()
			.requestMatchers("/swagger-ui/**").permitAll()
			.requestMatchers("/swagger-ui.html").permitAll()
			.requestMatchers("/swagger-resources/**").permitAll()
			.requestMatchers("/webjars/**").permitAll()
			.requestMatchers("/internal/**").permitAll()
			.anyRequest().authenticated())
		.sessionManagement(session -> session
			.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
		.addFilterBefore(securityContextFilter, UsernamePasswordAuthenticationFilter.class);

	return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
	CorsConfiguration configuration = new CorsConfiguration();

	configuration.setAllowedOrigins(List.of(
		"http://localhost:3000",
		"http://localhost:5173"
	));
	configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
	configuration.setAllowedHeaders(Arrays.asList(
		"Authorization", 
		"Content-Type", 
		"X-Requested-With",
		"User-Agent",
		"X-Device-ID",
		"X-User-Id",
		"X-Device-Id"
	));
	configuration.setExposedHeaders(List.of("Authorization"));
	configuration.setAllowCredentials(true);
	configuration.setMaxAge(3600L);

	UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
	source.registerCorsConfiguration("/**", configuration);

	return source;
    }
}
