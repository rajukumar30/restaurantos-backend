package com.restaurantos.config;

import com.restaurantos.config.repository.PlatformAdminBootstrapDao;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformAdminBootstrapTest {
    private final PlatformAdminBootstrapDao dao=mock(PlatformAdminBootstrapDao.class);
    private final PasswordEncoder encoder=mock(PasswordEncoder.class);
    private final DefaultApplicationArguments args=new DefaultApplicationArguments(new String[0]);

    @Test void blankCredentialsSkipDatabase() {
        new PlatformAdminBootstrap(dao,encoder,"","","Admin").run(args);
        verifyNoInteractions(dao,encoder);
    }
    @Test void partialCredentialsFailFast() {
        assertThrows(IllegalStateException.class,
            () -> new PlatformAdminBootstrap(dao,encoder,"admin@example.com","short","Admin").run(args));
        verifyNoInteractions(dao,encoder);
    }
    @Test void existingPlatformAdminIsIdempotent() {
        when(dao.adminExists("admin@example.com")).thenReturn(true);
        new PlatformAdminBootstrap(dao,encoder,"admin@example.com","longpassword123","Admin").run(args);
        verify(dao,never()).grantPlatformAdmin(any());
    }
    @Test void existingUserCanBeGrantedAdminAccess() {
        UUID id=UUID.randomUUID();
        when(dao.existingUser("admin@example.com")).thenReturn(id);
        new PlatformAdminBootstrap(dao,encoder,"admin@example.com","longpassword123","Admin").run(args);
        verify(dao).grantPlatformAdmin(id);
        verify(dao,never()).createUser(any(),any(),any(),any());
    }
}
