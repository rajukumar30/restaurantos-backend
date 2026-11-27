package com.restaurantos.modules.auth.service.impl;
import com.restaurantos.modules.auth.JwtService;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.auth.dto.TenantMembership;
import com.restaurantos.modules.auth.dto.LoginRequest;
import com.restaurantos.modules.auth.entity.AuthUser;
import com.restaurantos.modules.auth.repository.AuthDao;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AuthServiceImplTest {
 private final AuthDao dao=mock(AuthDao.class);
 private final PasswordEncoder encoder=mock(PasswordEncoder.class);
 private final JwtService jwt=mock(JwtService.class);
 private final TenantFeatureAccess features=mock(TenantFeatureAccess.class);
 private final AuthServiceImpl service=new AuthServiceImpl(dao,encoder,jwt,features);
 @Test void unknownUserRejected(){
  when(dao.findByEmail("nobody@example.com")).thenReturn(List.of());
  assertThrows(ResponseStatusException.class,()->service.login(new LoginRequest("nobody@example.com","password")));
  verifyNoInteractions(encoder,jwt);
 }
 @Test void wrongPasswordRejected(){
  AuthUser user=new AuthUser(UUID.randomUUID(),"A","a@example.com","hash","ACTIVE");
  when(dao.findByEmail("a@example.com")).thenReturn(List.of(user));
  when(encoder.matches("bad","hash")).thenReturn(false);
  assertThrows(ResponseStatusException.class,()->service.login(new LoginRequest("a@example.com","bad")));
  verifyNoInteractions(jwt);
 }
 @Test void noActiveRoleRejected(){
  AuthUser user=new AuthUser(UUID.randomUUID(),"A","a@example.com","hash","ACTIVE");
  when(dao.findByEmail("a@example.com")).thenReturn(List.of(user));
  when(encoder.matches("good","hash")).thenReturn(true);
  assertThrows(ResponseStatusException.class,()->service.login(new LoginRequest("a@example.com","good")));
  verifyNoInteractions(jwt);
 }
 @Test void kitchenOnlyAccountCannotLoginWithoutKitchenFeature(){
  UUID userId=UUID.randomUUID(),tenant=UUID.randomUUID();
  when(dao.findByEmail("kitchen@example.com")).thenReturn(List.of(new AuthUser(userId,"Kitchen","kitchen@example.com","hash","ACTIVE")));
  when(encoder.matches("good","hash")).thenReturn(true);
  when(dao.activeMembershipsWithRoles(userId)).thenReturn(List.of(new AuthDao.MembershipWithRoles(new TenantMembership(tenant,"Restaurant","Kitchen"),List.of("KITCHEN"))));
  assertThrows(ResponseStatusException.class,()->service.login(new LoginRequest("kitchen@example.com","good")));
  verify(features).enabled(tenant,"KITCHEN_DISPLAY");
  verifyNoInteractions(jwt);
 }
 @Test void waiterOnlyAccountCannotLoginWithoutWaiterFeature(){
  UUID userId=UUID.randomUUID(),tenant=UUID.randomUUID();
  when(dao.findByEmail("waiter@example.com")).thenReturn(List.of(new AuthUser(userId,"Waiter","waiter@example.com","hash","ACTIVE")));
  when(encoder.matches("good","hash")).thenReturn(true);
  when(dao.activeMembershipsWithRoles(userId)).thenReturn(List.of(new AuthDao.MembershipWithRoles(new TenantMembership(tenant,"Restaurant","Waiter"),List.of("WAITER"))));
  assertThrows(ResponseStatusException.class,()->service.login(new LoginRequest("waiter@example.com","good")));
  verify(features).enabled(tenant,"WAITER_DASHBOARD");
  verifyNoInteractions(jwt);
 }
 @Test void ownerCanLoginEvenWhenKitchenAndWaiterAreUnavailable(){
  UUID userId=UUID.randomUUID(),tenant=UUID.randomUUID();
  when(dao.findByEmail("owner@example.com")).thenReturn(List.of(new AuthUser(userId,"Owner","owner@example.com","hash","ACTIVE")));
  when(encoder.matches("good","hash")).thenReturn(true);
  when(dao.activeMembershipsWithRoles(userId)).thenReturn(List.of(new AuthDao.MembershipWithRoles(new TenantMembership(tenant,"Restaurant","Owner"),List.of("OWNER"))));
  assertDoesNotThrow(()->service.login(new LoginRequest("owner@example.com","good")));
 }
}
