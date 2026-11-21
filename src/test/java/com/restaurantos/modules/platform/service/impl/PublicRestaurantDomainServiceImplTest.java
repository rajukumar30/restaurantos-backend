package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.repository.PublicRestaurantDomainDao;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicRestaurantDomainServiceImplTest {
    private final PublicRestaurantDomainDao dao = mock(PublicRestaurantDomainDao.class);
    private final PublicRestaurantDomainServiceImpl service = new PublicRestaurantDomainServiceImpl(dao);

    @Test
    void resolvesProvisionedSubdomainCaseInsensitively() {
        when(dao.findActiveSlugBySubdomain("pizza")).thenReturn(Optional.of("pizza-house"));
        assertEquals("pizza-house", service.resolveSlug("PiZzA"));
        verify(dao).findActiveSlugBySubdomain("pizza");
    }

    @Test
    void rejectsInvalidSubdomainsWithoutDatabaseLookup() {
        for (String name : new String[] {"", "-bad", "bad-", "a.b", "a/b", "a b"}) {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.resolveSlug(name));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }
        verifyNoInteractions(dao);
    }

    @Test
    void returnsNotFoundForUnknownOrInactiveRestaurant() {
        when(dao.findActiveSlugBySubdomain("unknown")).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> service.resolveSlug("unknown"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }
}
