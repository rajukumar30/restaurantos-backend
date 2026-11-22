package com.restaurantos.modules.platform.service.impl;

import com.restaurantos.modules.platform.dto.RestaurantOnboardingDto.*;
import com.restaurantos.modules.platform.repository.RestaurantOnboardingDao;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RestaurantOnboardingServiceImplTest {
    private final RestaurantOnboardingDao dao = mock(RestaurantOnboardingDao.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final RestaurantOnboardingServiceImpl service = new RestaurantOnboardingServiceImpl(dao, encoder);
    private CreateRestaurantRequest request(String name) {
        return new CreateRestaurantRequest(name, "Main", "Owner", "Owner@EXAMPLE.com",
            "a very safe password", null, null, null);
    }
    @Test void preservesSlugAndPasswordHashing() {
        when(encoder.encode(anyString())).thenReturn("bcrypt-hash");
        RestaurantCreated created = service.create(request("Sample Restaurant"));
        assertEquals("sample-restaurant", created.slug());
        assertEquals("/r/sample-restaurant/menu", created.restaurantUrl());
        verify(dao).insertOwner(eq(created.ownerUserId()), eq("Owner"), eq("owner@example.com"),
            isNull(), eq("bcrypt-hash"));
        verify(dao).grantAllPermissions(any());
    }
    @Test void rejectsDuplicateSlugBeforeDatabaseMutation() {
        when(dao.slugExists("sample")).thenReturn(true);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> service.create(request("Sample"))).getStatusCode());
        verify(dao, never()).insertTenant(any(), any(), any(), any());
    }
    @Test void rejectsInvalidSlug() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
            () -> service.create(request("!!!"))).getStatusCode());
        verifyNoInteractions(dao);
    }
    @Test void rejectsOverlongSlugBeforeWriting() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
            () -> service.create(request("a".repeat(64)))).getStatusCode());
        verifyNoInteractions(dao);
    }
    @Test void trimsOwnerEmailBeforeCheckingUniquenessAndPersisting() {
        when(encoder.encode(anyString())).thenReturn("bcrypt-hash");
        CreateRestaurantRequest req = new CreateRestaurantRequest("New Place","Main","Owner",
            "  Owner@EXAMPLE.com  ","a very safe password",null,null,null);
        service.create(req);
        verify(dao).emailExists("Owner@EXAMPLE.com");
        verify(dao).insertOwner(any(),eq("Owner"),eq("owner@example.com"),isNull(),eq("bcrypt-hash"));
    }
    @Test void reservedSubdomainsAreRenamed() {
        RestaurantCreated created = service.create(request("admin"));
        assertEquals("restaurant-admin", created.subdomain());
    }
}
