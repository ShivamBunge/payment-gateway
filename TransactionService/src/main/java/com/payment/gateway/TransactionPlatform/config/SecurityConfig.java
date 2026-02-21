package com.payment.gateway.TransactionPlatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Disable CSRF (Common for REST APIs / microservices)
                .csrf(csrf -> csrf.disable())

                // 2. Authorize requests
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/payments/**").permitAll() // Allow our payment endpoint
                        .anyRequest().authenticated()
                )

                // 3. Use Basic Auth for everything else
                .httpBasic(withDefaults());

        return http.build();
    }
}