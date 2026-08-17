package com.saas.billing.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saas.billing.common.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication
        .UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority
        .SimpleGrantedAuthority;
import org.springframework.security.core.context
        .SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/auth/") || path.startsWith("/webhooks/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(
                HttpHeaders.AUTHORIZATION);

        if (authHeader == null
                || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtUtil.parseAndValidate(
                    token, "access");

            UUID orgId = UUID.fromString(
                    claims.get("orgId", String.class));
            String userId = claims.getSubject();
            String role = claims.get("role", String.class);

            TenantContext.setOrgId(orgId);

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            List.of(new SimpleGrantedAuthority(
                                    "ROLE_" + role))
                    );

            SecurityContextHolder.getContext()
                    .setAuthentication(auth);

            filterChain.doFilter(request, response);

        } catch (JwtException e) {
            SecurityContextHolder.clearContext();
            writeErrorResponse(response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    private void writeErrorResponse(
            HttpServletResponse response,
            int status,
            String message) throws IOException {

        Map<String, Object> errorBody = Map.of(
                "timestamp",
                LocalDateTime.now().toString(),
                "status", status,
                "error", message
        );

        response.setStatus(status);
        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(), errorBody);
    }
}