package com.restaurantos.modules.auth.service.impl;
import com.restaurantos.modules.auth.JwtService;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.auth.dto.*;
import com.restaurantos.modules.auth.entity.AuthUser;
import com.restaurantos.modules.auth.repository.AuthDao;
import com.restaurantos.modules.auth.service.AuthService;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.TimeUnit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Locale;
@Service
public class AuthServiceImpl implements AuthService {
 private static final Logger log=LoggerFactory.getLogger(AuthServiceImpl.class);
 private final AuthDao dao;
 private final PasswordEncoder passwordEncoder;
 private final JwtService jwt;
 private final TenantFeatureAccess features;
 public AuthServiceImpl(AuthDao dao,PasswordEncoder passwordEncoder,JwtService jwt,TenantFeatureAccess features){
  this.dao=dao;this.passwordEncoder=passwordEncoder;this.jwt=jwt;this.features=features;
 }
 @Override
 @Transactional(readOnly=true)
 public LoginResponse login(LoginRequest request){
  long start=System.nanoTime();
  List<AuthUser> users=dao.findByEmail(request.email());
  long userLookup=System.nanoTime();
  if(users.isEmpty()||!"ACTIVE".equals(users.getFirst().status())||
     !passwordEncoder.matches(request.password(),users.getFirst().passwordHash()))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
  long passwordVerified=System.nanoTime();
  AuthUser user=users.getFirst();
  List<String> authorities=new ArrayList<>();
  if(dao.isPlatformAdmin(user.id()))authorities.add("PLATFORM_ADMIN");
  List<TenantMembership> memberships=dao.activeMemberships(user.id()).stream()
   .filter(m->allowedMembership(user.id(),m.tenantId())).toList();
  long membershipsChecked=System.nanoTime();
  if(!memberships.isEmpty())authorities.add("TENANT_USER");
  if(authorities.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Account disabled or access denied. Contact your restaurant administrator.");
  String token=jwt.issue(user.id(),user.email(),authorities);
  long finished=System.nanoTime();
  log.info("Login timing lookupMs={} passwordMs={} membershipAndPermissionsMs={} jwtMs={} totalMs={} membershipCount={}",
   TimeUnit.NANOSECONDS.toMillis(userLookup-start),
   TimeUnit.NANOSECONDS.toMillis(passwordVerified-userLookup),
   TimeUnit.NANOSECONDS.toMillis(membershipsChecked-passwordVerified),
   TimeUnit.NANOSECONDS.toMillis(finished-membershipsChecked),
   TimeUnit.NANOSECONDS.toMillis(finished-start), memberships.size());
  return new LoginResponse(token,"Bearer",user.id(),user.name(),user.email(),authorities,memberships);
 }
 private boolean allowedMembership(java.util.UUID userId,java.util.UUID tenantId){
  Set<String> roles=new java.util.HashSet<>(dao.roleKeys(userId,tenantId).stream()
   .map(r->r.toUpperCase(Locale.ROOT)).toList());
  if(roles.isEmpty())return false;
  // Owner or other non-operational roles retain access to restaurant administration.
  if(roles.stream().anyMatch(r->!r.equals("KITCHEN")&&!r.equals("KITCHEN_STAFF")
      &&!r.equals("WAITER")&&!r.equals("WAITER_STAFF")))return true;
  return (roles.stream().anyMatch(r->r.equals("KITCHEN")||r.equals("KITCHEN_STAFF"))
       &&features.enabled(tenantId,"KITCHEN_DISPLAY"))
      ||(roles.stream().anyMatch(r->r.equals("WAITER")||r.equals("WAITER_STAFF"))
       &&features.enabled(tenantId,"WAITER_DASHBOARD"));
 }

}
