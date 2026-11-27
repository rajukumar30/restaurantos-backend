package com.restaurantos.modules.orders.service.impl;
import com.restaurantos.modules.orders.dto.CheckoutDto.*;
import com.restaurantos.modules.orders.repository.CheckoutDao;
import com.restaurantos.modules.orders.service.CheckoutService;
import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.staff.repository.StaffBillingDao;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.realtime.dto.RealtimeEvent;
import com.restaurantos.modules.realtime.service.RealtimeEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
@Service
public class CheckoutServiceImpl implements CheckoutService{
 private final CheckoutDao dao;private final StaffBillingDao billing;private final TenantAccessService access;private final TenantFeatureAccess features;private final RealtimeEventPublisher events;
 public CheckoutServiceImpl(CheckoutDao dao,StaffBillingDao billing,TenantAccessService access,TenantFeatureAccess features,RealtimeEventPublisher events){this.dao=dao;this.billing=billing;this.access=access;this.features=features;this.events=events;}
 private ResponseStatusException conflict(String s){return new ResponseStatusException(HttpStatus.CONFLICT,s);}
 private Scope scope(UUID tenant,UUID branch,UUID visit){return dao.scope(tenant,branch,visit).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Bill not found"));}
 private UUID authorize(UUID tenant,UUID branch,Authentication auth){UUID actor=access.requirePermission(auth,tenant,branch,"payments:read");access.requireBranchInTenant(tenant,branch);features.require(tenant,"GUEST_ORDERING");if(!billing.cashier(tenant,branch,actor))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Cashier, manager or owner access required");return actor;}
 private void notify(Scope s){events.publishBillingAfterCommit(new RealtimeEvent(UUID.randomUUID(),"BILL_CHANGED",s.tenant(),s.branch(),s.visit(),1,Instant.now(),Map.of("tableId",s.table().toString())));}
 private void begin(Scope s){var b=dao.bill(s);if("PAYMENT_PENDING".equals(b.status()))return;if(!"OPEN".equals(b.status())||b.orderCount()==0||b.unfinished()!=0||b.due().signum()<=0)throw conflict("All orders must be served and an outstanding balance must exist before checkout.");dao.begin(s,b);notify(s);}
 @Transactional
 public Detail guest(String qr,UUID order,boolean start){var s=dao.guest(qr,order).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Bill not found for this order"));features.require(s.tenant(),"GUEST_ORDERING");if(start){dao.lock(s);begin(s);}return dao.detail(s);}
 @Transactional(readOnly=true)
 public Page page(UUID tenant,UUID branch,String filter,String search,int page,int size,Authentication auth){authorize(tenant,branch,auth);if(!Set.of("all","pending","paid").contains(filter)||search.length()>100||page<1||page>100000||size<1||size>50)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid bill filters");return dao.page(tenant,branch,filter,search.trim(),page,size);}
 @Transactional(readOnly=true)
 public Detail detail(UUID tenant,UUID branch,UUID visit,Authentication auth){authorize(tenant,branch,auth);return dao.detail(scope(tenant,branch,visit));}
 @Transactional
 public Detail change(UUID tenant,UUID branch,UUID visit,boolean start,Authentication auth){authorize(tenant,branch,auth);var s=scope(tenant,branch,visit);dao.lock(s);if(start)begin(s);else{if(!"PAYMENT_PENDING".equals(dao.bill(s).status()))throw conflict("Bill is not awaiting payment");dao.resume(s);notify(s);}return dao.detail(s);}
 @Transactional
 public Detail confirm(UUID tenant,UUID visit,Confirm r,Authentication auth){
  UUID actor=authorize(tenant,r.branchId(),auth);var s=scope(tenant,r.branchId(),visit);dao.lock(s);
  String method=r.method().trim().toUpperCase(Locale.ROOT),ref=r.reference()==null||r.reference().isBlank()?null:r.reference().trim().toUpperCase(Locale.ROOT);
  if(!Set.of("CASH","UPI","CARD").contains(method)||("UPI".equals(method)||"CARD".equals(method))&&ref==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a verified transaction reference for UPI or card.");
  var old=dao.replay(s,r.paymentKey());if(old.isPresent()){var p=old.get();if(!visit.equals(p.get("visit_id"))||!Objects.equals(p.get("provider"),"MANUAL_"+method)||!Objects.equals(p.get("provider_order_id"),ref)||((BigDecimal)p.get("amount")).compareTo(r.amount())!=0)throw conflict("Payment key already used");return dao.detail(s);}
  var b=dao.bill(s);var frozen=dao.frozen(s);
  if(!"PAYMENT_PENDING".equals(b.status())||b.unfinished()!=0||b.due().signum()<=0||frozen==null||frozen.compareTo(b.due())!=0||b.due().compareTo(r.amount())!=0)throw conflict("The bill amount or status changed. Refresh before collecting payment.");
  if(dao.referenceUsed(s,method,ref))throw conflict("This transaction reference has already been recorded.");
  var allocations=dao.allocations(s);if(allocations.stream().anyMatch(a->a.due().signum()<0)||allocations.stream().map(Allocation::due).reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(b.due())!=0)throw conflict("Payment balances changed. Refresh before collecting.");
  dao.record(s,r.paymentKey(),method,ref,r.amount());boolean first=true;
  for(var a:allocations)if(a.due().signum()>0){dao.allocate(s,a,method,first?ref:null,actor);first=false;}
  dao.settle(s);notify(s);return dao.detail(s);
 }
 @Transactional
 public Merchant merchant(UUID tenant,UUID branch,Merchant update,Authentication auth){
  authorize(tenant,branch,auth);if(update==null)return dao.merchant(tenant,branch);
  if(!access.hasTenantWidePermission(auth,tenant,"payments:read"))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Owner access required");
  String upi=update.upiId()==null?"":update.upiId().trim(),name=update.merchantName()==null?"":update.merchantName().trim();
  if(!upi.isEmpty()&&(!upi.matches("[A-Za-z0-9._-]{2,100}@[A-Za-z0-9.-]{2,50}")||name.isBlank()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid merchant UPI ID and name.");
  var m=new Merchant(upi.isEmpty()?null:upi,name.isEmpty()?null:name);dao.merchant(tenant,branch,m);return m;
 }
}
