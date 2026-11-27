package com.restaurantos.modules.staff.service.impl;

import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.staff.dto.StaffAdminDto.*;
import com.restaurantos.modules.staff.repository.StaffAdministrationDao;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StaffAdministrationServiceImplTest {
    private final StaffAdministrationDao dao = mock(StaffAdministrationDao.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final TenantAccessService access = mock(TenantAccessService.class);
    private final StaffAdministrationServiceImpl service = new StaffAdministrationServiceImpl(dao,encoder,access);
    private final UUID tenant = UUID.randomUUID(), branch = UUID.randomUUID();
    private final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(UUID.randomUUID(),null);
    private InviteStaffRequest req(String role) {
        return new InviteStaffRequest("Test Staff", "Test@EXAMPLE.com", null, "temporary-password", branch, role);
    }
    @Test void rejectsUnsupportedRoles() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
            () -> service.invite(tenant, req("OWNER"), auth)).getStatusCode());
        verify(dao, never()).createUser(any(),any(),any(),any(),any());
    }
    @Test void onlyTenantWideManagerCanAssignManager() {
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
            () -> service.invite(tenant, req("MANAGER"), auth)).getStatusCode());
        verify(dao, never()).createUser(any(),any(),any(),any(),any());
    }
    @Test void createsWaiterWithBranchScopeAndLowercaseEmail() {
        when(encoder.encode(anyString())).thenReturn("hash");
        var created = service.invite(tenant,req("waiter"),auth);
        assertEquals("WAITER",created.roleKey());
        verify(dao).createUser(eq(created.userId()),eq("Test Staff"),eq("test@example.com"),isNull(),eq("hash"));
        verify(dao).createBranchUser(any(),eq(tenant),eq(branch),eq(created.tenantUserId()));
        verify(dao).grantPermissions(any(),argThat(perms -> perms.contains("orders:manage")));
    }
    @Test void checksAccessBeforeListingStaff() {
        service.list(tenant,branch,auth);
        var sequence = inOrder(access,dao);
        sequence.verify(access).requirePermission(auth,tenant,branch,"staff:read");
        sequence.verify(dao).list(tenant,branch,false);
    }
    @Test void editRequiresTenantWideManagement(){
        UUID user=UUID.randomUUID();
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,()->service.update(tenant,user,new UpdateStaffRequest(branch,"Staff",null,"WAITER","INACTIVE"),auth)).getStatusCode());
        verifyNoInteractions(dao);
    }
    @Test void protectedAccountCannotBeEdited(){
        when(access.hasTenantWidePermission(auth,tenant,"staff:manage")).thenReturn(true);
        UUID user=UUID.randomUUID();
        when(dao.lockEditableMembership(tenant,branch,user)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,()->service.update(tenant,user,new UpdateStaffRequest(branch,"Staff",null,"WAITER","INACTIVE"),auth)).getStatusCode());
        verify(dao,never()).updateStaff(any(),any(),any(),any(),any(),any(),any(),any());
    }
    @Test void editsSingleBranchAccountAndUsesInactiveStatus(){
        when(access.hasTenantWidePermission(auth,tenant,"staff:manage")).thenReturn(true);
        UUID user=UUID.randomUUID(),membership=UUID.randomUUID(),role=UUID.randomUUID();
        when(dao.lockEditableMembership(tenant,branch,user)).thenReturn(Optional.of(membership));
        when(dao.findRole(tenant,"WAITER")).thenReturn(role);
        var result=new StaffMember(user,"Staff","staff@example.com",null,"INACTIVE","Waiter","Main branch");
        when(dao.list(tenant,branch,false)).thenReturn(List.of(result));
        assertEquals(result,service.update(tenant,user,new UpdateStaffRequest(branch,"Staff",null,"WAITER","INACTIVE"),auth));
        verify(dao).updateStaff(tenant,branch,membership,user,"Staff",null,"INACTIVE",role);
    }
}
