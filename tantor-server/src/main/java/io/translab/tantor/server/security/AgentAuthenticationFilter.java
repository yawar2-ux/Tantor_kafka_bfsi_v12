package io.translab.tantor.server.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Production agent authentication filter.
 *
 * Agents are machine identities and should not use user JWTs. Every agent request
 * must carry a shared bootstrap token in X-Tantor-Agent-Token, preferably over mTLS.
 * The token itself must be supplied from environment/secret manager and never stored
 * in the repository. This filter gives agent endpoints a dedicated ROLE_AGENT
 * authority after constant-time token validation.
 */
public class AgentAuthenticationFilter extends OncePerRequestFilter {

    public static final String AGENT_TOKEN_HEADER = "X-Tantor-Agent-Token";
    public static final String AGENT_HOST_HEADER = "X-Tantor-Agent-Host";

    private final String expectedToken;
    private final boolean enabled;

    public AgentAuthenticationFilter(
            @Value("${tantor.agent-auth.token:}") String expectedToken,
            @Value("${tantor.agent-auth.enabled:true}") boolean enabled) {
        this.expectedToken = expectedToken == null ? "" : expectedToken.trim();
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/agents/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!StringUtils.hasText(expectedToken) || isUnsafeDefault(expectedToken)) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Agent authentication token is not configured on the control plane");
            return;
        }

        String suppliedToken = request.getHeader(AGENT_TOKEN_HEADER);
        if (!StringUtils.hasText(suppliedToken) || !constantTimeEquals(expectedToken, suppliedToken.trim())) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing agent token");
            return;
        }

        String hostId = request.getHeader(AGENT_HOST_HEADER);
        String principal = StringUtils.hasText(hostId) ? "agent:" + hostId : "agent";
        var auth = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_AGENT"), new SimpleGrantedAuthority("AGENT_API"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = supplied.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    private boolean isUnsafeDefault(String token) {
        String upper = token.toUpperCase();
        return upper.contains("CHANGE_ME") || upper.contains("DEFAULT") || upper.length() < 24;
    }
}
