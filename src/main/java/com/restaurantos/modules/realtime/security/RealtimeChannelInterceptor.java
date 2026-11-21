package com.restaurantos.modules.realtime.security;

import com.restaurantos.modules.auth.JwtService;
import com.restaurantos.modules.auth.repository.AuthDao;
import com.restaurantos.modules.auth.repository.PlatformAdminAccessDao;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.staff.repository.TenantAccessDao;
import io.jsonwebtoken.JwtException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import java.security.Principal;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RealtimeChannelInterceptor implements ChannelInterceptor {
    private static final Pattern ORDER_TOPIC = Pattern.compile(
        "^/topic/tenants/([0-9a-fA-F-]{36})/branches/([0-9a-fA-F-]{36})/(orders|service-requests)$");
    private final JwtService jwt;
    private final AuthDao auth;
    private final PlatformAdminAccessDao admins;
    private final TenantAccessDao access;
    private final TenantFeatureAccess features;

    public RealtimeChannelInterceptor(JwtService jwt, AuthDao auth, PlatformAdminAccessDao admins,
                                      TenantAccessDao access, TenantFeatureAccess features) {
        this.jwt = jwt;
        this.auth = auth;
        this.admins = admins;
        this.access = access;
        this.features = features;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) return message;
        StompCommand command = headers.getCommand();
        if (command == null) return message;
        if (command == StompCommand.CONNECT) {
            String bearer = headers.getFirstNativeHeader("Authorization");
            if (bearer == null || !bearer.startsWith("Bearer ")) throw new SecurityException("Authentication required");
            try {
                UUID userId = UUID.fromString(jwt.parse(bearer.substring(7)).getSubject());
                if (!admins.active(userId) && auth.activeMemberships(userId).isEmpty())
                    throw new SecurityException("Account access revoked");
                Date expiration = jwt.parse(bearer.substring(7)).getExpiration();
                if (expiration == null || !expiration.toInstant().isAfter(Instant.now()))
                    throw new SecurityException("Expired credentials");
                if (headers.getSessionAttributes() != null)
                    headers.getSessionAttributes().put("jwtExpiresAt", expiration.toInstant().toEpochMilli());
                headers.setUser(new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            } catch (JwtException | IllegalArgumentException e) {
                throw new SecurityException("Invalid or expired credentials");
            }
        } else if (command == StompCommand.SUBSCRIBE) {
            Principal principal = headers.getUser();
            if (!(principal instanceof UsernamePasswordAuthenticationToken token)
                    || !(token.getPrincipal() instanceof UUID userId))
                throw new SecurityException("Authentication required");
            Object expiresAt = headers.getSessionAttributes() == null ? null : headers.getSessionAttributes().get("jwtExpiresAt");
            if (!(expiresAt instanceof Long expiry) || expiry <= System.currentTimeMillis())
                throw new SecurityException("Session expired; reconnect required");
            String destination = headers.getDestination();
            Matcher match = ORDER_TOPIC.matcher(destination == null ? "" : destination);
            if (!match.matches()) throw new SecurityException("Subscription not allowed");
            UUID tenantId = UUID.fromString(match.group(1));
            UUID branchId = UUID.fromString(match.group(2));
            String topic = match.group(3);
            if (!access.activeBranchExists(tenantId, branchId)
                    || !access.hasPermission(userId, tenantId, branchId, "orders:read")
                    || !features.enabled(tenantId, "service-requests".equals(topic) ? "SERVICE_REQUESTS" : "GUEST_ORDERING"))
                throw new SecurityException("Subscription access denied");
        } else if (command == StompCommand.SEND) {
            // Clients never publish events directly; business mutations use authenticated REST.
            throw new SecurityException("Client publishing is disabled");
        }
        return message;
    }
}
