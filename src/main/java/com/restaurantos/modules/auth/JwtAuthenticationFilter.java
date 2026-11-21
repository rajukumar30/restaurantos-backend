package com.restaurantos.modules.auth;

import com.restaurantos.modules.auth.repository.PlatformAdminAccessDao;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.*;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final PlatformAdminAccessDao adminAccess;

    public JwtAuthenticationFilter(JwtService jwtService, PlatformAdminAccessDao adminAccess) {
        this.jwtService = jwtService;
        this.adminAccess = adminAccess;
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
                // Revalidate revoked platform accounts even when their signed JWT has not expired.
                if (request.getRequestURI().startsWith("/api/v1/platform/")
                    && !adminAccess.active(userId)) {
                    SecurityContextHolder.clearContext();
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Platform administrator access revoked");
                    return;
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
