package com.restaurantos.modules.platform.controller;

import com.restaurantos.modules.auth.repository.AuthDao;
import com.restaurantos.modules.platform.repository.PlatformInvoiceDao;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnerBillingControllerTest {
    private final AuthDao auth=mock(AuthDao.class);
    private final PlatformInvoiceDao invoices=mock(PlatformInvoiceDao.class);
    private final OwnerBillingController controller=new OwnerBillingController(auth,invoices);

    @Test void rejectsUnauthenticatedAccess() {
        var e=assertThrows(ResponseStatusException.class,()->controller.overview(UUID.randomUUID(),null));
        assertEquals(HttpStatus.UNAUTHORIZED,e.getStatusCode());
        verifyNoInteractions(auth,invoices);
    }

    @Test void rejectsCrossTenantAndStaffAccess() {
        UUID user=UUID.randomUUID(),tenant=UUID.randomUUID();
        when(auth.roleKeys(user,tenant)).thenReturn(List.of("WAITER"));
        var e=assertThrows(ResponseStatusException.class,()->controller.overview(
            tenant,new UsernamePasswordAuthenticationToken(user,null)));
        assertEquals(HttpStatus.FORBIDDEN,e.getStatusCode());
        verifyNoInteractions(invoices);
    }

    @Test void ownerMayReadOnlyTheirTenantInvoices() {
        UUID user=UUID.randomUUID(),tenant=UUID.randomUUID();
        when(auth.roleKeys(user,tenant)).thenReturn(List.of("OWNER"));
        when(invoices.list(tenant)).thenReturn(List.of());
        var result=controller.overview(tenant,new UsernamePasswordAuthenticationToken(user,null));
        assertEquals(true,result.get("success"));
        verify(invoices).list(tenant);
    }
}
