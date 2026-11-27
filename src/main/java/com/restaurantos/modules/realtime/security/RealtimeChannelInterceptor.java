package com.restaurantos.modules.realtime.security;

import com.restaurantos.modules.auth.JwtService;
import com.restaurantos.modules.dining.service.DiningSessionService;
import com.restaurantos.modules.auth.repository.AuthDao;
import com.restaurantos.modules.auth.repository.PlatformAdminAccessDao;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.staff.repository.TenantAccessDao;
import com.restaurantos.modules.orders.repository.OrderQueryDao;
import com.restaurantos.modules.service.repository.ServiceRequestDao;
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
        "^/topic/tenants/([0-9a-fA-F-]{36})/branches/([0-9a-fA-F-]{36})/(orders|service-requests|billing|floor)$");
    private static final Pattern GUEST_ORDER_TOPIC = Pattern.compile("^/topic/guest/orders/([0-9a-fA-F-]{36})$");
    private static final Pattern GUEST_SERVICE_TOPIC = Pattern.compile("^/topic/guest/tables/([0-9a-fA-F-]{36})/(service-requests|bill)$");
    private static final Pattern GUEST_MENU_TOPIC = Pattern.compile("^/topic/guest/branches/([0-9a-fA-F-]{36})/menu$");
    private final ServiceRequestDao serviceRequests;
    private final DiningSessionService dining;
    private final OrderQueryDao orders;
    private final JwtService jwt;
    private final AuthDao auth;
    private final PlatformAdminAccessDao admins;
    private final TenantAccessDao access;
    private final TenantFeatureAccess features;

    public RealtimeChannelInterceptor(JwtService jwt, AuthDao auth, PlatformAdminAccessDao admins,
                                      TenantAccessDao access, TenantFeatureAccess features, OrderQueryDao orders, ServiceRequestDao serviceRequests,DiningSessionService dining) {
        this.serviceRequests = serviceRequests;this.dining=dining;
        this.orders = orders;
        this.jwt = jwt;
        this.auth = auth;
        this.admins = admins;
        this.access = access;
        this.features = features;
    }

    /**
     * Revalidate every broker delivery for an existing subscription. Returning null drops
     * the message if a JWT expired, an account was revoked or permissions changed.
     * This does not close the socket; it prevents further delivery of protected events.
     */
    public Message<?> authorizeOutbound(Message<?> message) {
        StompHeaderAccessor outbound = StompHeaderAccessor.wrap(message);
        if (outbound.getCommand() != StompCommand.MESSAGE || outbound.getSubscriptionId() == null)
            return message;
        StompHeaderAccessor check = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        check.setDestination(outbound.getDestination());
        check.setUser(outbound.getUser());
        check.setSessionAttributes(outbound.getSessionAttributes());
        check.setLeaveMutable(true);
        try {
            preSend(org.springframework.messaging.support.MessageBuilder.createMessage(
                    new byte[0], check.getMessageHeaders()), null);
            return message;
        } catch (SecurityException ex) {
            return null;
        }
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) return message;
        StompCommand command = headers.getCommand();
        if (command == null) return message;
        if (command == StompCommand.CONNECT) {
            String bearer = headers.getFirstNativeHeader("Authorization");
            if (bearer == null || !bearer.startsWith("Bearer ")) {
                String guestQr = headers.getFirstNativeHeader("X-Guest-QR");
                if (guestQr == null || guestQr.isBlank() || guestQr.length() > 256)
                    throw new SecurityException("Authentication required");
                if (headers.getSessionAttributes() == null) throw new SecurityException("Session unavailable");
                headers.getSessionAttributes().put("guestQr", guestQr);
                String session=headers.getFirstNativeHeader("X-Dining-Session");if(session!=null&&session.length()==64)headers.getSessionAttributes().put("diningSession",session);
                return message;
            }
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
            String destination = headers.getDestination();
            Matcher guest = GUEST_ORDER_TOPIC.matcher(destination == null ? "" : destination);
            if (guest.matches()) {
                Object qr = headers.getSessionAttributes() == null ? null : headers.getSessionAttributes().get("guestQr");
                if (!(qr instanceof String qrToken) || orders.publicStatus(qrToken, UUID.fromString(guest.group(1))).isEmpty())
                    throw new SecurityException("Guest order subscription denied");
                return message;
            }
            Matcher guestService = GUEST_SERVICE_TOPIC.matcher(destination == null ? "" : destination);
            if (guestService.matches()) {
                Object qr = headers.getSessionAttributes() == null ? null : headers.getSessionAttributes().get("guestQr");
                if (!(qr instanceof String qrToken)) throw new SecurityException("Guest table authentication required");
                Object session=headers.getSessionAttributes().get("diningSession");
                if(!(session instanceof String proof)||!dining.socketAllowed(qrToken,proof,UUID.fromString(guestService.group(1))))throw new SecurityException("Dining session required");
                var tables = serviceRequests.tables(qrToken);
                if (tables.isEmpty() || !tables.getFirst().id().equals(UUID.fromString(guestService.group(1)))
                        || !features.enabled(tables.getFirst().tenantId(), "bill".equals(guestService.group(2))?"GUEST_ORDERING":"SERVICE_REQUESTS"))
                    throw new SecurityException("Guest service subscription denied");
                return message;
            }
            Matcher guestMenu = GUEST_MENU_TOPIC.matcher(destination == null ? "" : destination);
            if (guestMenu.matches()) {
                Object qr = headers.getSessionAttributes() == null ? null : headers.getSessionAttributes().get("guestQr");
                if (!(qr instanceof String qrToken)) throw new SecurityException("Guest table authentication required");
                var tables = serviceRequests.tables(qrToken);
                if (tables.isEmpty() || !tables.getFirst().branchId().equals(UUID.fromString(guestMenu.group(1))))
                    throw new SecurityException("Guest menu subscription denied");
                return message;
            }
            Principal principal = headers.getUser();
            if (!(principal instanceof UsernamePasswordAuthenticationToken token)
                    || !(token.getPrincipal() instanceof UUID userId))
                throw new SecurityException("Authentication required");
            Object expiresAt = headers.getSessionAttributes() == null ? null : headers.getSessionAttributes().get("jwtExpiresAt");
            if (!(expiresAt instanceof Long expiry) || expiry <= System.currentTimeMillis())
                throw new SecurityException("Session expired; reconnect required");
            // Recheck account revocation for each new subscription, not only at CONNECT.
            if (!admins.active(userId) && auth.activeMemberships(userId).isEmpty())
                throw new SecurityException("Account access revoked");
            Matcher match = ORDER_TOPIC.matcher(destination == null ? "" : destination);
            if (!match.matches()) throw new SecurityException("Subscription not allowed");
            UUID tenantId = UUID.fromString(match.group(1));
            UUID branchId = UUID.fromString(match.group(2));
            String topic = match.group(3);
            if ("floor".equals(topic)) {
                if(!access.activeBranchExists(tenantId,branchId)||!access.hasPermission(userId,tenantId,branchId,"floor:read")||!features.enabled(tenantId,"GUEST_ORDERING"))throw new SecurityException("Floor access denied");
                return message;
            }
            if (!access.activeBranchExists(tenantId, branchId)
                    || !access.hasPermission(userId, tenantId, branchId, "billing".equals(topic) ? "payments:read" : "orders:read")
                    || (!"billing".equals(topic) && !features.enabled(tenantId, "service-requests".equals(topic) ? "SERVICE_REQUESTS" : "GUEST_ORDERING")))
                throw new SecurityException("Subscription access denied");
        } else if (command == StompCommand.SEND) {
            // Clients never publish events directly; business mutations use authenticated REST.
            throw new SecurityException("Client publishing is disabled");
        }
        return message;
    }
}
