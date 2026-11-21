package com.restaurantos.modules.auth.service.impl;
import com.restaurantos.modules.auth.JwtService;
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
 private final AuthServiceImpl service=new AuthServiceImpl(dao,encoder,jwt);
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
}
