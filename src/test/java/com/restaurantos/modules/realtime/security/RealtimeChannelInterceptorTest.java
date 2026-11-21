package com.restaurantos.modules.realtime.security;

import com.restaurantos.modules.auth.JwtService;
import com.restaurantos.modules.auth.repository.AuthDao;
import com.restaurantos.modules.auth.repository.PlatformAdminAccessDao;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.staff.repository.TenantAccessDao;
import com.restaurantos.modules.orders.repository.OrderQueryDao;
import com.restaurantos.modules.service.repository.ServiceRequestDao;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeChannelInterceptorTest {
    private JwtService jwt;
    private AuthDao auth;
    private PlatformAdminAccessDao admins;
    private TenantAccessDao access;
    private TenantFeatureAccess features;
    private RealtimeChannelInterceptor interceptor;
    private OrderQueryDao orders;
    private final UUID user = UUID.randomUUID();
    private final UUID tenant = UUID.randomUUID();
    private final UUID branch = UUID.randomUUID();

    @BeforeEach
    void setup() {
        jwt = mock(JwtService.class);
        auth = mock(AuthDao.class);
        admins = mock(PlatformAdminAccessDao.class);
        when(admins.active(user)).thenReturn(true);
        access = mock(TenantAccessDao.class);
        features = mock(TenantFeatureAccess.class);
        orders = mock(OrderQueryDao.class);
        interceptor = new RealtimeChannelInterceptor(jwt, auth, admins, access, features, orders, mock(ServiceRequestDao.class));
    }

    private org.springframework.messaging.Message<byte[]> message(StompHeaderAccessor headers) {
        if (headers.getCommand() == StompCommand.SUBSCRIBE && headers.getSessionAttributes() == null) {
            headers.setSessionAttributes(new java.util.HashMap<>(java.util.Map.of("jwtExpiresAt", System.currentTimeMillis() + 60000L)));
        }
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test
    void rejectsUnauthenticatedConnection() {
        assertThrows(SecurityException.class, () -> interceptor.preSend(
            message(StompHeaderAccessor.create(StompCommand.CONNECT)), null));
    }

    @Test
    void rejectsClientPublishing() {
        assertThrows(SecurityException.class, () -> interceptor.preSend(
            message(StompHeaderAccessor.create(StompCommand.SEND)), null));
    }

    @Test
    void rejectsUnapprovedTopics() {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setUser(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        headers.setDestination("/topic/platform/admin");
        assertThrows(SecurityException.class, () -> interceptor.preSend(message(headers), null));
    }

    @Test
    void rejectsCrossTenantSubscriptions() {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setUser(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        headers.setDestination("/topic/tenants/" + tenant + "/branches/" + branch + "/orders");
        when(access.activeBranchExists(tenant, branch)).thenReturn(true);
        assertThrows(SecurityException.class, () -> interceptor.preSend(message(headers), null));
    }

    @Test
    void allowsAuthorizedOrderSubscription() {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setUser(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        headers.setDestination("/topic/tenants/" + tenant + "/branches/" + branch + "/orders");
        when(access.activeBranchExists(tenant, branch)).thenReturn(true);
        when(access.hasPermission(user, tenant, branch, "orders:read")).thenReturn(true);
        when(features.enabled(tenant, "GUEST_ORDERING")).thenReturn(true);
        assertNotNull(interceptor.preSend(message(headers), null));
    }
    @Test
    void allowsAuthorizedServiceRequestSubscription() {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setUser(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        headers.setDestination("/topic/tenants/" + tenant + "/branches/" + branch + "/service-requests");
        when(access.activeBranchExists(tenant, branch)).thenReturn(true);
        when(access.hasPermission(user, tenant, branch, "orders:read")).thenReturn(true);
        when(features.enabled(tenant, "SERVICE_REQUESTS")).thenReturn(true);
        assertNotNull(interceptor.preSend(message(headers), null));
    }

    @Test
    void rejectsServiceRequestSubscriptionWithoutEntitlement() {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setUser(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        headers.setDestination("/topic/tenants/" + tenant + "/branches/" + branch + "/service-requests");
        when(access.activeBranchExists(tenant, branch)).thenReturn(true);
        when(access.hasPermission(user, tenant, branch, "orders:read")).thenReturn(true);
        assertThrows(SecurityException.class, () -> interceptor.preSend(message(headers), null));
    }

    @Test
    void rejectsServiceRequestSubscriptionForOtherTenant() {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setUser(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        headers.setDestination("/topic/tenants/" + tenant + "/branches/" + branch + "/service-requests");
        when(access.activeBranchExists(tenant, branch)).thenReturn(true);
        when(features.enabled(tenant, "SERVICE_REQUESTS")).thenReturn(true);
        assertThrows(SecurityException.class, () -> interceptor.preSend(message(headers), null));
    }

    @Test
    void rejectsExpiredSessionEvenWithExistingPermission() {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setUser(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        headers.setSessionAttributes(new java.util.HashMap<>(java.util.Map.of("jwtExpiresAt", System.currentTimeMillis() - 1000L)));
        headers.setDestination("/topic/tenants/" + tenant + "/branches/" + branch + "/orders");
        when(access.activeBranchExists(tenant, branch)).thenReturn(true);
        when(access.hasPermission(user, tenant, branch, "orders:read")).thenReturn(true);
        when(features.enabled(tenant, "GUEST_ORDERING")).thenReturn(true);
        assertThrows(SecurityException.class, () -> interceptor.preSend(message(headers), null));
    }

    @Test
    void rejectsRevokedStaffAccountOnNewSubscription() {
        when(admins.active(user)).thenReturn(false);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setUser(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        headers.setDestination("/topic/tenants/" + tenant + "/branches/" + branch + "/orders");
        when(access.activeBranchExists(tenant, branch)).thenReturn(true);
        when(access.hasPermission(user, tenant, branch, "orders:read")).thenReturn(true);
        when(features.enabled(tenant, "GUEST_ORDERING")).thenReturn(true);
        assertThrows(SecurityException.class, () -> interceptor.preSend(message(headers), null));
    }

    @Test
    void rejectsGuestSubscriptionWithoutQrProof() {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination("/topic/guest/orders/" + UUID.randomUUID());
        assertThrows(SecurityException.class, () -> interceptor.preSend(message(headers), null));
    }

    @Test
    void rejectsGuestSubscriptionToOrderNotOnQrTable() {
        UUID order = UUID.randomUUID();
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setSessionAttributes(new java.util.HashMap<>(java.util.Map.of("guestQr", "other-table-qr")));
        headers.setDestination("/topic/guest/orders/" + order);
        assertThrows(SecurityException.class, () -> interceptor.preSend(message(headers), null));
        verify(orders).publicStatus("other-table-qr", order);
    }

    @Test
    void rejectsGuestConnectionWithoutCredentials() {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        assertThrows(SecurityException.class, () -> interceptor.preSend(message(headers), null));
    }

}
