package com.restaurantos.modules.staff.repository;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TenantAccessDaoTest {
    @Test void tenantPermissionUsesBoundParametersAndTypedNullableBranch() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(), any(), any(), any(), any()))
            .thenReturn(1);
        UUID user = UUID.randomUUID(), tenant = UUID.randomUUID();
        assertTrue(new TenantAccessDao(jdbc).hasPermission(user,tenant,null,"staff:read"));
        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(),eq(Integer.class),
            eq(tenant),eq(user),eq("staff:read"),isNull(),isNull());
        assertTrue(sql.getValue().contains("CAST(? AS UUID) IS NOT NULL"));
    }
}
