package io.translab.tantor.server.config;

import io.translab.tantor.server.security.AgentAuthenticationFilter;
import io.translab.tantor.server.security.JwtAuthenticationFilter;
import io.translab.tantor.server.security.JwtUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtUtils jwtUtils;
    private final boolean swaggerEnabled;

    public SecurityConfig(JwtUtils jwtUtils,
                          @Value("${tantor.security.swagger-public:false}") boolean swaggerEnabled) {
        this.jwtUtils = jwtUtils;
        this.swaggerEnabled = swaggerEnabled;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter authenticationJwtTokenFilter() {
        return new JwtAuthenticationFilter(jwtUtils);
    }

    @Bean
    public AgentAuthenticationFilter agentAuthenticationFilter(
            @Value("${tantor.agent-auth.token:}") String token,
            @Value("${tantor.agent-auth.enabled:true}") boolean enabled) {
        return new AgentAuthenticationFilter(token, enabled);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AgentAuthenticationFilter agentAuthenticationFilter) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/api/v1/auth/**", "/error", "/actuator/health", "/actuator/info").permitAll();
                if (swaggerEnabled) {
                    auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll();
                }
                auth.requestMatchers("/api/v1/agents/**").hasAuthority("AGENT_API");
                auth.requestMatchers("/api/v1/approvals/**", "/api/v1/audit/**", "/api/v1/governance/**",
                                     "/api/v1/reconciliation/**", "/api/v1/secrets/**").authenticated();
                auth.requestMatchers("/api/v1/**").authenticated();
                auth.anyRequest().authenticated();
            });

        http.addFilterBefore(agentAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
