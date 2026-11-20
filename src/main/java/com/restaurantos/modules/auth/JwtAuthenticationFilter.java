package com.restaurantos.modules.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final JdbcTemplate jdbc;

    public JwtAuthenticationFilter(JwtService jwtService, JdbcTemplate jdbc) {
        this.jwtService = jwtService;
        this.jdbc = jdbc;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                UUID userId = UUID.fromString(claims.getSubject());
                List<?> roles = claims.get("authorities", List.class);
                var authorities = roles == null ? List.<SimpleGrantedAuthority>of() :
                    roles.stream().map(String::valueOf).map(SimpleGrantedAuthority::new).toList();
                // A signed token alone cannot preserve revoked platform access.
                if (request.getRequestURI().startsWith("/api/v1/platform/")) {
                    Integer active = jdbc.queryForObject("""
                        SELECT COUNT(*) FROM platform_admins pa
                        JOIN app_users u ON u.id=pa.user_id
                        WHERE pa.user_id=? AND pa.status='ACTIVE' AND u.status='ACTIVE'
                        """, Integer.class, userId);
                    if (active == null || active == 0) {
                        SecurityContextHolder.clearContext();
                        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Platform administrator access revoked");
                        return;
                    }
                }
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}