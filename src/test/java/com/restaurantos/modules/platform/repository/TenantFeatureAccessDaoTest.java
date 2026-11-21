package com.restaurantos.modules.platform.repository;

import org.junit.jupiter.api.Test;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TenantFeatureAccessDaoTest {
 private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
 private final TenantFeatureAccessDao dao=new TenantFeatureAccessDao(jdbc);
 @Test void unknownTenantFailsClosed(){
  UUID tenant=UUID.randomUUID();
  when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(tenant),eq("QR_MENU")))
   .thenThrow(new EmptyResultDataAccessException(1));
  assertFalse(dao.enabled(tenant,"QR_MENU"));
 }
 @Test void malformedKeyNeverQueriesDatabase(){
  assertFalse(dao.enabled(UUID.randomUUID(),"../../QR_MENU"));
  assertFalse(dao.enabled(null,"QR_MENU"));
  verifyNoInteractions(jdbc);
 }
 @Test void disabledFeatureFailsClosed(){
  UUID tenant=UUID.randomUUID();
  when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(tenant),eq("QR_MENU"))).thenReturn(false);
  assertFalse(dao.enabled(tenant,"QR_MENU"));
 }
}
