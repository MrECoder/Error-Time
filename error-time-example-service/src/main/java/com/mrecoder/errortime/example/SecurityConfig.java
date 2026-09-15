package com.mrecoder.errortime.example;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * This application has no authentication of its own to demonstrate - its
 * subject is error handling - but shipping every actuator endpoint wide open
 * would be a real, easy-to-copy-into-a-real-service anti-pattern. {@code
 * /demo/**} stays public (that's the whole demo); {@code /actuator/health}
 * and {@code /actuator/info} stay public (the standard exception, for load
 * balancer/orchestrator probes); everything else - {@code /actuator/prometheus}
 * and {@code /actuator/metrics} in particular, both of which leak internal
 * error-rate and topology data - requires authentication.
 *
 * <p>No {@code spring.security.user.password} is configured, so Spring Boot
 * generates and logs a random one at startup ({@code Using generated
 * security password: ...}) - set {@code SPRING_SECURITY_USER_NAME}/
 * {@code SPRING_SECURITY_USER_PASSWORD} for a stable one instead of reading
 * the log every restart.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/demo/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Stateless JSON API authenticated per-request via HTTP Basic, not cookies/sessions -
            // nothing here for a cross-site request to forge.
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
