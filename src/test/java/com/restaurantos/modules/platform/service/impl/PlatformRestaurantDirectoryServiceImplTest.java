package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.dto.PlatformRestaurantDto.*;
import com.restaurantos.modules.platform.repository.PlatformRestaurantDirectoryDao;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlatformRestaurantDirectoryServiceImplTest {
    private final PlatformRestaurantDirectoryDao dao = mock(PlatformRestaurantDirectoryDao.class);
    private final PlatformRestaurantDirectoryServiceImpl service = new PlatformRestaurantDirectoryServiceImpl(dao);
    private final UUID tenant = UUID.randomUUID(), actor = UUID.randomUUID();
    private UsernamePasswordAuthenticationToken auth() { return new UsernamePasswordAuthenticationToken(actor, null); }
    private Restaurant restaurant() { return new Restaurant(tenant, "Original", "original", "original", "ACTIVE", null); }

    @Test void validatesSearchAndPagination() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
            () -> service.list("a".repeat(101), 0)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
            () -> service.list("", -1)).getStatusCode());
        verifyNoInteractions(dao);
    }
    @Test void updatingRestaurantRetainsUnspecifiedFieldsAndAudits() {
        when(dao.find(tenant)).thenReturn(restaurant());
        service.update(tenant, new UpdateRestaurant(null, "INACTIVE"), auth());
        verify(dao).updateRestaurant(tenant, "Original", "INACTIVE");
        verify(dao).audit(actor, tenant, "RESTAURANT_PROFILE_UPDATED");
    }
    @Test void rejectsUnsupportedStatus() {
        when(dao.find(tenant)).thenReturn(restaurant());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
            () -> service.update(tenant, new UpdateRestaurant(null, "DELETED"), auth())).getStatusCode());
        verify(dao, never()).updateRestaurant(any(), any(), any());
    }
    @Test void branchUpdateIsTenantScoped() {
        UUID branch = UUID.randomUUID();
        when(dao.find(tenant)).thenReturn(restaurant());
        when(dao.branchForUpdate(tenant, branch)).thenReturn(
            new BranchDetail(branch, "Main", "MAIN", null, "ACTIVE", "Asia/Kolkata", "{}"));
        BranchDetail updated = service.updateBranch(tenant, branch,
            new UpdateBranch("New name", "1234", null), auth());
        assertEquals("MAIN", updated.branchCode());
        verify(dao).updateBranch(tenant, branch, "New name", "1234", "ACTIVE");
    }
}
