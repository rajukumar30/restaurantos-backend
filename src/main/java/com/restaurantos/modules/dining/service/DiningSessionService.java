package com.restaurantos.modules.dining.service;
import com.restaurantos.modules.dining.dto.DiningDto.*;
import com.restaurantos.modules.dining.repository.DiningDao;
import com.restaurantos.modules.orders.dto.CheckoutDto.Scope;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.staff.TenantAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Service
public class DiningSessionService{
 private final DiningDao dao;private final TenantAccessService access;private final TenantFeatureAccess features;private final PasswordEncoder encoder;
 private final SecureRandom random=new SecureRandom();
 public DiningSessionService(DiningDao dao,TenantAccessService access,TenantFeatureAccess features,PasswordEncoder encoder){this.dao=dao;this.access=access;this.features=features;this.encoder=encoder;}
 private ResponseStatusException deny(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Dining session expired or unavailable. Ask staff for your current dining code.");}
 private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.FORBIDDEN,"Invalid or expired dining code. Ask staff for the current code.");}
 static String hash(String token){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 @Transactional
 public Code issue(UUID tenant,UUID table,CodeRequest r,Authentication auth){
  access.requirePermission(auth,tenant,r.branchId(),"floor:manage");access.requireBranchInTenant(tenant,r.branchId());features.require(tenant,"GUEST_ORDERING");
  if(!dao.lockTable(tenant,r.branchId(),table))throw deny();
  String code=String.format(Locale.ROOT,"%06d",random.nextInt(1000000));Instant expiry=Instant.now().plusSeconds(12*3600);
  if(!dao.issue(new Scope(tenant,r.branchId(),table,r.visitId()),encoder.encode(code),expiry))throw new ResponseStatusException(HttpStatus.CONFLICT,"Only the current open visit can receive a dining code.");
  return new Code(code,r.visitId(),expiry);
 }
 @Transactional(noRollbackFor=ResponseStatusException.class)
 public Session join(String qr,String code){
  if(code==null||!code.matches("[0-9]{6}")||!dao.lockQr(qr))throw invalid();
  var a=dao.active(qr).orElseThrow(this::invalid);features.require(a.scope().tenant(),"GUEST_ORDERING");
  if(a.hash()==null||a.expiresAt()==null||!a.expiresAt().isAfter(Instant.now())||a.failures()>=5)throw invalid();
  if(!encoder.matches(code,a.hash())){dao.fail(a.scope().visit());throw invalid();}
  if(dao.sessions(a.scope().visit())>=24)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Dining session limit reached. Ask staff for assistance.");
  byte[] bytes=new byte[32];random.nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);
  Instant expiry=Instant.now().plusSeconds(12*3600);
  dao.insert(UUID.randomUUID(),a.scope().visit(),hash(token),expiry);return new Session(token,a.scope().visit(),expiry);
 }
 @Transactional(propagation=Propagation.MANDATORY)
 public Visit require(String qr,String token,boolean lock,boolean openOnly){
  if(token==null||!token.matches("[0-9a-f]{64}"))throw deny();
  if(lock&&!dao.lockQr(qr))throw deny();
  var v=dao.find(qr,hash(token)).orElseThrow(this::deny);features.require(v.scope().tenant(),"GUEST_ORDERING");
  if(openOnly&&!"OPEN".equals(v.status()))throw new ResponseStatusException(HttpStatus.CONFLICT,"This table bill is being settled. Contact staff.");
  return v;
 }
 public boolean socketAllowed(String qr,String token,UUID table){return token!=null&&token.matches("[0-9a-f]{64}")&&dao.find(qr,hash(token)).filter(v->v.scope().table().equals(table)).isPresent();}
 public void requireOrder(Visit v,UUID order){if(!dao.hasOrder(v,order))throw deny();}
 public String orderKey(Visit v,String key){
  if(key==null||!key.matches("[A-Za-z0-9_-]{16,100}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A valid order submission key is required.");
  return hash(v.scope().visit()+":"+v.sessionId()+":"+key);
 }
 public void checkOrderLimits(Visit v,long units,int lines,String key){
  if(dao.repeated(v,key))return;
  if(units>50||lines>50||dao.recent(v)>=6)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Large or repeated orders need staff assistance. Please check with your waiter.");
 }
 public void recordOrder(Visit v,UUID order){dao.record(v,order);}
}
