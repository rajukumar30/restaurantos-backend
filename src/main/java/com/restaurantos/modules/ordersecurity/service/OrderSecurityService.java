package com.restaurantos.modules.ordersecurity.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantos.modules.ordersecurity.dto.OrderSecurityDto.*;
import com.restaurantos.modules.ordersecurity.repository.OrderSecurityDao;
import com.restaurantos.modules.dining.repository.DiningDao;
import com.restaurantos.modules.dining.service.DiningSessionService;
import com.restaurantos.modules.dining.dto.DiningDto.Visit;
import com.restaurantos.modules.orders.dto.CheckoutDto.Scope;
import com.restaurantos.modules.orders.dto.OrderDto.*;
import com.restaurantos.modules.orders.repository.OrderPlacementDao;
import com.restaurantos.modules.orders.service.OrderPlacementService;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.realtime.dto.RealtimeEvent;
import com.restaurantos.modules.realtime.service.RealtimeEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.Instant;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Service
public class OrderSecurityService{
 private final OrderSecurityDao dao;private final DiningDao sessions;private final DiningSessionService dining;private final OrderPlacementDao orders;private final OrderPlacementService placement;private final TenantAccessService access;private final TenantFeatureAccess features;private final RealtimeEventPublisher events;private final ObjectMapper mapper;
 private final SecureRandom random=new SecureRandom();
 public OrderSecurityService(OrderSecurityDao dao,DiningDao sessions,DiningSessionService dining,OrderPlacementDao orders,OrderPlacementService placement,TenantAccessService access,TenantFeatureAccess features,RealtimeEventPublisher events,ObjectMapper mapper){this.dao=dao;this.sessions=sessions;this.dining=dining;this.orders=orders;this.placement=placement;this.access=access;this.features=features;this.events=events;this.mapper=mapper;}
 private ResponseStatusException conflict(String msg){return new ResponseStatusException(HttpStatus.CONFLICT,msg);}
 private ResponseStatusException forbidden(String msg){return new ResponseStatusException(HttpStatus.FORBIDDEN,msg);}
 private ResponseStatusException limit(){return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many order requests. Ask restaurant staff for assistance.");}
 private static String hash(String input){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
 private Scope table(String qr){var s=dao.table(qr).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Table not available."));features.require(s.tenant(),"GUEST_ORDERING");return s;}
 private UUID staff(UUID tenant,UUID branch,Authentication auth,String perm){UUID actor=access.requirePermission(auth,tenant,branch,perm);access.requireBranchInTenant(tenant,branch);features.require(tenant,"GUEST_ORDERING");return actor;}
 @Transactional(readOnly=true) public Access publicAccess(String qr){var s=table(qr);return new Access(dao.settings(s.tenant(),s.branch()).mode());}
 @Transactional(readOnly=true) public Settings settings(UUID tenant,UUID branch,Authentication auth){staff(tenant,branch,auth,"floor:read");return dao.settings(tenant,branch);}
 @Transactional public Settings update(UUID tenant,Update r,Authentication auth){UUID actor=staff(tenant,r.branchId(),auth,"floor:manage");if(!access.hasTenantWidePermission(auth,tenant,"staff:manage"))throw forbidden("Owner access required.");if(r.maxVisitAmount().compareTo(r.maxOrderAmount())<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Visit limit must cover the order limit.");if("EXPRESS".equals(r.mode())&&!r.acknowledgeRisk())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Acknowledge Express mode's saved QR risk.");dao.save(tenant,r,actor);return dao.settings(tenant,r.branchId());}
 @Transactional public Joined join(String qr){
  if(!sessions.lockQr(qr))throw forbidden("Table unavailable.");var s=table(qr);
  if("SECURE".equals(dao.settings(s.tenant(),s.branch()).mode()))throw forbidden("Enter the dining code to join.");
  if(s.visit()==null)throw conflict("Ask staff to seat your group first.");
  if(dao.unverifiedSessions(s.visit())>=24)throw limit();
  byte[] raw=new byte[32];random.nextBytes(raw);String token=HexFormat.of().formatHex(raw);Instant expiry=Instant.now().plusSeconds(12*3600);
  dao.join(UUID.randomUUID(),s.visit(),hash(token),expiry);return new Joined(token,s.visit(),expiry,false);
 }
 @Transactional(readOnly=true) public Map<String,Object> status(String qr,String token){var v=dining.requireOrdering(qr,token,false,false);return Map.of("visitId",v.scope().visit(),"status",v.status(),"verified",v.verified(),"mode",dao.settings(v.scope().tenant(),v.scope().branch()).mode());}
 private void notify(Scope s,UUID id){events.publishFloorAfterCommit(RealtimeEvent.orderChanged("FLOOR_CHANGED",s.tenant(),s.branch(),s.table()));}
 private String json(PlaceOrderRequest r){try{return mapper.writeValueAsString(r);}catch(Exception e){throw new IllegalStateException("Cannot serialize order",e);}}
 private PlaceOrderRequest parse(String json){try{return mapper.readValue(json,PlaceOrderRequest.class);}catch(Exception e){throw new IllegalStateException("Cannot read reviewed order",e);}}
 private record Quote(BigDecimal total,int units,String items){}
 private Quote quote(Scope s,PlaceOrderRequest r){
  if(r.items().size()>50)throw limit();var quantities=new LinkedHashMap<UUID,Integer>();
  for(var line:r.items()){quantities.merge(line.menuItemId(),line.quantity(),Integer::sum);}
  if(quantities.values().stream().anyMatch(n->n>20)||quantities.values().stream().mapToInt(n->n).sum()>50)throw limit();
  var lines=orders.findAvailableItems(s.tenant(),s.branch(),quantities);
  if(lines.size()!=quantities.size())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"One or more dishes are unavailable.");
  var total=lines.stream().map(l->l.unitPrice().multiply(BigDecimal.valueOf(l.quantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);
  String items=String.join(", ",lines.stream().map(l->l.itemName()+" × "+l.quantity()).toList());return new Quote(total,quantities.values().stream().mapToInt(n->n).sum(),items.substring(0,Math.min(4000,items.length())));
 }
 static String reviewReason(Settings s,boolean verified,BigDecimal total,int units,BigDecimal previous){
  if("STRICT".equals(s.mode()))return "Every order requires staff review";
  if("EXPRESS".equals(s.mode())&&(!verified&&(units>s.maxUnits()||total.compareTo(s.maxOrderAmount())>0||previous.add(total).compareTo(s.maxVisitAmount())>0)))return "Express limit exceeded";
  return null;
 }
 @Transactional public OrderReceipt submit(String qr,String token,PlaceOrderRequest request,String idempotencyKey){
  var v=dining.requireOrdering(qr,token,true,true);var s=v.scope();var mode=dao.settings(s.tenant(),s.branch());
  if("SECURE".equals(mode.mode())&&!v.verified())throw forbidden("Enter the dining code to continue.");
  String key=dining.orderKey(v,idempotencyKey),json=json(request),digest=hash(json);
  var old=dao.replay(s,key);if(old.isPresent()){var x=old.get();if(!x.sessionId().equals(v.sessionId())&&!v.verified()||!x.hash().equals(digest))throw conflict("Submission key already used.");if("APPROVED".equals(x.status()))return placement.placeChecked(qr,request,key,s.visit(),x.review().id(),null);return new OrderReceipt(x.review().id(),x.review().orderNumber(),x.review().tableName(),x.review().total(),"PENDING".equals(x.status())&&x.review().expiresAt().isAfter(Instant.now())?"AWAITING_REVIEW":"CANCELLED");}
  // Existing accepted-order retries must never enter review or consume the Express limit twice.
  if(sessions.repeated(v,key))return placement.placeChecked(qr,request,key,s.visit(),null,null);
  dining.checkOrderLimits(v,request.items().stream().mapToLong(l->l.quantity()).sum(),request.items().size(),key);
  if("SECURE".equals(mode.mode())){var receipt=placement.placeChecked(qr,request,key,s.visit(),null,null);dining.recordOrder(v,receipt.orderId());return receipt;}
  var quote=quote(s,request);
  String reason=reviewReason(mode,v.verified(),quote.total(),quote.units(),v.verified()?BigDecimal.ZERO:dao.unverifiedTotal(s.visit()));
  if(reason==null){var receipt=placement.placeChecked(qr,request,key,s.visit(),null,quote.total());dining.recordOrder(v,receipt.orderId());if(!v.verified())dao.markUnverified(v,receipt.orderId());return receipt;}
  if(dao.pending(s.visit())>=6||dao.recentRequests(s.visit())>=6)throw limit();
  UUID id=UUID.randomUUID();String number="R"+id.toString().replace("-","").substring(0,9).toUpperCase(Locale.ROOT);
  dao.insert(v,id,number,key,digest,json,quote.total(),quote.items(),reason,Instant.now().plusSeconds(600));notify(s,id);
  String tableName=orders.findActiveTable(qr).getFirst().displayName();
  return new OrderReceipt(id,number,tableName,quote.total(),"AWAITING_REVIEW");
 }
 @Transactional(readOnly=true) public List<Review> list(UUID tenant,UUID branch,Authentication auth){staff(tenant,branch,auth,"floor:manage");return dao.list(tenant,branch);}
 @Transactional public Map<String,String> decide(UUID tenant,UUID id,Decision r,Authentication auth){
  UUID actor=staff(tenant,r.branchId(),auth,"floor:manage");
  var first=dao.find(tenant,r.branchId(),id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Review not found."));
  var s=new Scope(tenant,r.branchId(),first.review().tableId(),first.review().visitId());
  if(!sessions.lockTable(tenant,r.branchId(),s.table()))throw conflict("Table unavailable.");
  var x=dao.find(tenant,r.branchId(),id).orElseThrow(()->conflict("Review changed."));
  if(!"PENDING".equals(x.status())||!x.review().expiresAt().isAfter(Instant.now())||!dao.open(s))throw conflict("Review expired or dining visit changed.");
  if("APPROVE".equals(r.action())){
   if(!dao.sessionActive(s,x.sessionId()))throw conflict("Guest session expired. Ask guest to resubmit.");
   var receipt=placement.placeChecked(dao.qr(s),parse(x.json()),x.key(),s.visit(),id,x.review().total());
   if(!receipt.orderId().equals(id))throw conflict("Order submission changed.");
   dining.recordOrder(new Visit(s,x.sessionId(),"OPEN",true),id);
   dao.verifySession(s,x.sessionId());dao.resolve(s,id,actor,"APPROVED",r.reason().trim());
  }else{dao.resolve(s,id,actor,"REJECTED",r.reason().trim());events.publishAfterCommit(new RealtimeEvent(UUID.randomUUID(),"ORDER_STATUS_CHANGED",tenant,r.branchId(),id,1,Instant.now(),Map.of("tableId",s.table().toString())));}
  notify(s,id);return Map.of("status","APPROVE".equals(r.action())?"APPROVED":"REJECTED");
 }
}
