package com.restaurantos.modules.platform;

import com.restaurantos.modules.platform.repository.RestaurantFeatureAccessDao;
import com.restaurantos.modules.platform.repository.RestaurantFeatureAccessDao.Access;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RestaurantFeatureAccessServiceTest {
    private final RestaurantFeatureAccessDao dao=mock(RestaurantFeatureAccessDao.class);
    private final UUID tenant=UUID.randomUUID();

    @Test void disabledEnforcementDoesNotHitDatabase() {
        new RestaurantFeatureAccessService(dao,false,7).require(tenant,"GUEST_ORDERING");
        verifyNoInteractions(dao);
    }
    @Test void grandfatheredTenantWithoutPlanIsAllowed() {
        new RestaurantFeatureAccessService(dao,true,7).require(tenant,"GUEST_ORDERING");
        verify(dao).hasAnySubscription(tenant);
    }
    @Test void expiredSubscriptionIsRejectedAfterGracePeriod() {
        when(dao.latestActive(tenant,"GUEST_ORDERING")).thenReturn(new Access(
            LocalDate.now().minusDays(40),LocalDate.now().minusDays(20),true,true,true));
        var service = new RestaurantFeatureAccessService(dao,true,7);
        assertEquals(HttpStatus.FORBIDDEN,assertThrows(ResponseStatusException.class,
            () -> service.require(tenant,"GUEST_ORDERING")).getStatusCode());
    }
}
