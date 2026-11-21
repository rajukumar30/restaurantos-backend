package com.restaurantos.modules.staff;

import com.restaurantos.modules.staff.repository.TenantAccessDao;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantAccessServiceTest {
    private final TenantAccessDao dao = mock(TenantAccessDao.class);
    private final TenantAccessService service = new TenantAccessService(dao);
    private final UUID user = UUID.randomUUID(), tenant = UUID.randomUUID(), branch = UUID.randomUUID();
    @Test void requirePermissionChecksTenantBranchScope() {
        when(dao.hasPermission(user,tenant,branch,"staff:manage")).thenReturn(true);
        assertEquals(user,service.requirePermission(new UsernamePasswordAuthenticationToken(user,null),
            tenant,branch,"staff:manage"));
        verify(dao).hasPermission(user,tenant,branch,"staff:manage");
    }
    @Test void unauthenticatedIsUnauthorized() {
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ResponseStatusException.class,
            () -> service.requirePermission(null,tenant,branch,"staff:manage")).getStatusCode());
        verifyNoInteractions(dao);
    }
    @Test void unrelatedTenantMembershipCannotAccessBranch() {
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,
            () -> service.requirePermission(new UsernamePasswordAuthenticationToken(user,null),
            tenant,branch,"staff:manage")).getStatusCode());
    }
    @Test void inactiveBranchIsNotFound() {
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,
            () -> service.requireBranchInTenant(tenant,branch)).getStatusCode());
    }
}
