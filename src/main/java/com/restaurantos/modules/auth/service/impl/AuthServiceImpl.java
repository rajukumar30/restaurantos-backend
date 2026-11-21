package com.restaurantos.modules.auth.service.impl;
import com.restaurantos.modules.auth.JwtService;
import com.restaurantos.modules.auth.dto.*;
import com.restaurantos.modules.auth.entity.AuthUser;
import com.restaurantos.modules.auth.repository.AuthDao;
import com.restaurantos.modules.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.List;
@Service
public class AuthServiceImpl implements AuthService {
 private final AuthDao dao;
 private final PasswordEncoder passwordEncoder;
 private final JwtService jwt;
 public AuthServiceImpl(AuthDao dao,PasswordEncoder passwordEncoder,JwtService jwt){
  this.dao=dao;this.passwordEncoder=passwordEncoder;this.jwt=jwt;
 }
 @Override
 @Transactional(readOnly=true)
 public LoginResponse login(LoginRequest request){
  List<AuthUser> users=dao.findByEmail(request.email());
  if(users.isEmpty()||!"ACTIVE".equals(users.getFirst().status())||
     !passwordEncoder.matches(request.password(),users.getFirst().passwordHash()))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
  AuthUser user=users.getFirst();
  List<String> authorities=new ArrayList<>();
  if(dao.isPlatformAdmin(user.id()))authorities.add("PLATFORM_ADMIN");
  List<TenantMembership> memberships=dao.activeMemberships(user.id());
  if(!memberships.isEmpty())authorities.add("TENANT_USER");
  if(authorities.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"No active platform access");
  String token=jwt.issue(user.id(),user.email(),authorities);
  return new LoginResponse(token,"Bearer",user.id(),user.name(),user.email(),authorities,memberships);
 }
}
