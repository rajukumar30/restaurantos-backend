package com.restaurantos.modules.orders.service.impl;
import com.restaurantos.modules.orders.dto.GuestCustomerDto.*;
import com.restaurantos.modules.orders.repository.GuestCustomerHistoryDao;
import com.restaurantos.modules.orders.service.GuestCustomerHistoryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
@Service
public class GuestCustomerHistoryServiceImpl implements GuestCustomerHistoryService {
 private static final String DEMO_CODE="123456";
 private final GuestCustomerHistoryDao dao;
 private final SecureRandom random=new SecureRandom();
 private final boolean demoEnabled;
 public GuestCustomerHistoryServiceImpl(GuestCustomerHistoryDao dao,
  @Value("${restaurantos.customer-auth.demo-otp-enabled:false}") boolean demoEnabled){
  this.dao=dao;this.demoEnabled=demoEnabled;
 }
 private UUID tenant(String qrToken){
  List<UUID> ids=dao.tenant(qrToken);
  if(ids.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Invalid restaurant QR");
  return ids.getFirst();
 }
 private void requireDemo(){
  if(!demoEnabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
   "SMS verification is not configured");
 }
 @Override @Transactional
 public OtpRequested request(String qrToken,PhoneRequest body){
  requireDemo();
  UUID tenantId=tenant(qrToken);
  if(dao.recentChallenges(tenantId,body.phone())>=3)
   throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Try again in an hour");
  dao.consumePrior(tenantId,body.phone());
  UUID id=UUID.randomUUID();
  dao.createChallenge(id,tenantId,body.phone());
  return new OtpRequested(id,300,DEMO_CODE);
 }
 @Override @Transactional
 public VerifiedSession verify(String qrToken,VerifyRequest body){
  requireDemo();
  UUID tenantId=tenant(qrToken);
  List<String> phones=dao.incrementAndReadPhone(body.challengeId(),tenantId);
  if(phones.isEmpty())
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Challenge expired or invalid");
  if(!MessageDigest.isEqual(DEMO_CODE.getBytes(StandardCharsets.UTF_8),body.code().getBytes(StandardCharsets.UTF_8)))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Incorrect verification code");
  dao.consumeChallenge(body.challengeId());
  byte[] secret=new byte[32];random.nextBytes(secret);
  String token=HexFormat.of().formatHex(secret);
  dao.createSession(sha256(token),tenantId,phones.getFirst());
  return new VerifiedSession(token,phones.getFirst(),Instant.now().plusSeconds(604800));
 }
 @Override @Transactional(readOnly=true)
 public List<CustomerOrder> orders(String qrToken,String authorization){
  UUID tenantId=tenant(qrToken);
  if(authorization==null||!authorization.startsWith("Bearer "))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Verification required");
  String token=authorization.substring(7);
  if(!token.matches("[a-f0-9]{64}"))
   throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid session");
  List<String> phones=dao.sessionPhones(sha256(token),tenantId);
  if(phones.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Session expired");
  return dao.orders(tenantId,phones.getFirst());
 }
 private static String sha256(String value){
  try{
   return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
    .digest(value.getBytes(StandardCharsets.UTF_8)));
  }catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
}
