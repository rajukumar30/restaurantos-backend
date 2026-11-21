package com.restaurantos.modules.staff.service.impl;

import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.staff.TenantBranchDao;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantBranchServiceImplTest {
    private final TenantBranchDao dao=mock(TenantBranchDao.class);
    private final TenantAccessService access=mock(TenantAccessService.class);
    private final TenantBranchServiceImpl service=new TenantBranchServiceImpl(dao,access);
    private final UUID user=UUID.randomUUID(),tenant=UUID.randomUUID();
    private final UsernamePasswordAuthenticationToken auth=new UsernamePasswordAuthenticationToken(user,null);
    @Test void requiresAuthentication() {
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ResponseStatusException.class,
            () -> service.list(tenant,null)).getStatusCode());
        verifyNoInteractions(dao,access);
    }
    @Test void deniesNonTenantStaffWithoutBranchPermission() {
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,
            () -> service.list(tenant,auth)).getStatusCode());
        verify(dao,never()).list(any(),any(),anyBoolean());
    }
    @Test void tenantWideStaffSeeAllActiveBranches() {
        when(access.hasTenantWidePermission(auth,tenant,"branch:read")).thenReturn(true);
        service.list(tenant,auth);
        verify(dao).list(tenant,user,true);
        verify(dao,never()).hasBranchReadPermission(any(),any());
    }
    @Test void branchStaffSeeOnlyScopedBranches() {
        when(dao.hasBranchReadPermission(tenant,user)).thenReturn(true);
        service.list(tenant,auth);
        verify(dao).list(tenant,user,false);
    }
}
