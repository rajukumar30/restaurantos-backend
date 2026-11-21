package com.restaurantos.modules.auth;

import com.restaurantos.modules.auth.repository.PlatformAdminAccessDao;
import com.restaurantos.modules.auth.repository.AuthDao;
import com.restaurantos.modules.platform.TenantFeatureAccess;
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
    private final AuthDao authDao;
    private final TenantFeatureAccess features;

    public JwtAuthenticationFilter(JwtService jwtService, PlatformAdminAccessDao adminAccess,AuthDao authDao,TenantFeatureAccess features) {
        this.jwtService = jwtService;
        this.adminAccess = adminAccess;
        this.authDao=authDao;
        this.features=features;
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
                // Recheck operational staff access on every tenant API call, including existing JWTs.
                String uri=request.getRequestURI();
                java.util.regex.Matcher tenantPath=java.util.regex.Pattern
                    .compile("^/api/v1/tenants/([0-9a-fA-F-]{36})(?:/|$)").matcher(uri);
                if(tenantPath.find() && !operationalAccess(userId,UUID.fromString(tenantPath.group(1)))){
                    SecurityContextHolder.clearContext();
                    response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Account disabled or access denied. Contact your restaurant administrator.");
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

    private boolean operationalAccess(UUID userId,UUID tenantId){
        List<String> roles=authDao.roleKeys(userId,tenantId).stream()
            .map(r->r.toUpperCase(Locale.ROOT)).toList();
        if(roles.isEmpty())return true; // Normal tenant authorization still applies downstream.
        if(roles.stream().anyMatch(r->!r.equals("KITCHEN")&&!r.equals("KITCHEN_STAFF")
            &&!r.equals("WAITER")&&!r.equals("WAITER_STAFF")))return true;
        return (roles.stream().anyMatch(r->r.equals("KITCHEN")||r.equals("KITCHEN_STAFF"))
                &&features.enabled(tenantId,"KITCHEN_DISPLAY"))
            ||(roles.stream().anyMatch(r->r.equals("WAITER")||r.equals("WAITER_STAFF"))
                &&features.enabled(tenantId,"WAITER_DASHBOARD"));
    }
}
