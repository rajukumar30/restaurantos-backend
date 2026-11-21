package com.restaurantos.modules.platform.service.impl;
import com.restaurantos.modules.platform.dto.PlatformFeatureGrantDto.Create;
import com.restaurantos.modules.platform.repository.PlatformFeatureGrantDao;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PlatformFeatureGrantServiceImplTest {
 private final PlatformFeatureGrantDao dao=mock(PlatformFeatureGrantDao.class);
 private final PlatformFeatureGrantServiceImpl service=new PlatformFeatureGrantServiceImpl(dao);
 private final UUID tenant=UUID.randomUUID();
 private final UsernamePasswordAuthenticationToken admin=
  new UsernamePasswordAuthenticationToken(UUID.randomUUID(),null);
 @Test void rejectsExpiredGrant(){
  ResponseStatusException error=assertThrows(ResponseStatusException.class,
   ()->service.create(tenant,new Create("QR_MENU",OffsetDateTime.now().minusDays(1)),admin));
  assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode());
  verify(dao,never()).insert(any(),anyString(),any(),any());
 }
 @Test void rejectsUnavailableFeature(){
  when(dao.activeTenant(tenant)).thenReturn(true);
  ResponseStatusException error=assertThrows(ResponseStatusException.class,
   ()->service.create(tenant,new Create("ONLINE_PAYMENTS",OffsetDateTime.now().plusDays(7)),admin));
  assertEquals(HttpStatus.CONFLICT,error.getStatusCode());
  verify(dao,never()).insert(any(),anyString(),any(),any());
 }
 @Test void requiresAdminPrincipal(){
  ResponseStatusException error=assertThrows(ResponseStatusException.class,
   ()->service.create(tenant,new Create("QR_MENU",OffsetDateTime.now().plusDays(7)),null));
  assertEquals(HttpStatus.UNAUTHORIZED,error.getStatusCode());
  verifyNoInteractions(dao);
 }
}
