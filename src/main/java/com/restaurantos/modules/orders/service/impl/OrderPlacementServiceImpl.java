package com.restaurantos.modules.orders.service.impl;
import com.restaurantos.modules.orders.dto.OrderDto.*;
import com.restaurantos.modules.orders.entity.*;
import com.restaurantos.modules.orders.repository.OrderPlacementDao;
import com.restaurantos.modules.orders.service.OrderPlacementService;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.realtime.dto.RealtimeEvent;
import com.restaurantos.modules.realtime.service.RealtimeEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
@Service
public class OrderPlacementServiceImpl implements OrderPlacementService {
 private final OrderPlacementDao dao;
 private final TenantFeatureAccess featureAccess;
 private final RealtimeEventPublisher events;
 public OrderPlacementServiceImpl(OrderPlacementDao dao,TenantFeatureAccess featureAccess,RealtimeEventPublisher events){
  this.dao=dao;this.featureAccess=featureAccess;this.events=events;
 }
 @Override
 @Transactional
 public OrderReceipt place(String qrToken,PlaceOrderRequest request,String idempotencyKey){
  if(idempotencyKey!=null && !idempotencyKey.matches("[A-Za-z0-9_-]{16,100}"))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid idempotency key.");
  List<QrTable> tables=dao.findActiveTable(qrToken);
  if(tables.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"This table QR code is invalid or inactive.");
  QrTable table=tables.getFirst();
  featureAccess.require(table.tenantId(),"GUEST_ORDERING");
  Map<UUID,Integer> quantities=new LinkedHashMap<>();
  for(OrderLineRequest line:request.items())quantities.merge(line.menuItemId(),line.quantity(),Integer::sum);
  if(quantities.values().stream().anyMatch(quantity->quantity>20))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A maximum of 20 units per menu item can be ordered.");
  List<ValidatedOrderLine> lines=new ArrayList<>();
  BigDecimal total=BigDecimal.ZERO;
  for(Map.Entry<UUID,Integer> entry:quantities.entrySet()){
   List<ValidatedOrderLine> available=dao.findAvailableItem(table.tenantId(),table.branchId(),entry.getKey(),entry.getValue());
   if(available.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"One or more selected menu items are no longer available.");
   ValidatedOrderLine line=available.getFirst();
   lines.add(line);
   total=total.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
  }
  String requestHash=null;
  if(idempotencyKey!=null){
   StringBuilder canonical=new StringBuilder(request.customerName().trim()).append('|')
    .append(request.customerPhone()).append('|')
    .append(request.notes()==null?"":request.notes().trim());
   quantities.entrySet().stream().sorted(Map.Entry.comparingByKey())
    .forEach(e->canonical.append('|').append(e.getKey()).append(':').append(e.getValue()));
   try{
    byte[] digest=MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
    requestHash=HexFormat.of().formatHex(digest);
   }catch(NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
  }
  dao.lockTable(table.tableId());
  if(idempotencyKey!=null){
   var existing=dao.findSubmission(table,idempotencyKey);
   if(!existing.isEmpty()){
    var old=existing.getFirst();
    if(!Objects.equals(old.get("requestHash"),requestHash))
     throw new ResponseStatusException(HttpStatus.CONFLICT,"This submission key was already used for a different order.");
    return new OrderReceipt((UUID)old.get("orderId"),(String)old.get("orderNumber"),
      table.displayName(),(BigDecimal)old.get("totalAmount"),(String)old.get("status"));
   }
  }
  List<UUID> visits=dao.activeVisits(table);
  UUID visitId;
  if(visits.isEmpty()){
   visitId=UUID.randomUUID();
   String billNumber="B"+UUID.randomUUID().toString().replace("-","").substring(0,12).toUpperCase(Locale.ROOT);
   dao.createVisit(visitId,table,billNumber);
  }else{
   visitId=visits.getFirst();
   if(!"OPEN".equals(dao.visitStatus(visitId)))
    throw new ResponseStatusException(HttpStatus.CONFLICT,"This table bill is being settled. Please contact the cashier.");
  }
  UUID orderId=UUID.randomUUID();
  String orderNumber="R"+UUID.randomUUID().toString().replace("-","").substring(0,9).toUpperCase(Locale.ROOT);
  String notes=request.notes()==null||request.notes().isBlank()?null:request.notes().trim();
  dao.createOrder(orderId,orderNumber,table,visitId,request.customerName().trim(),request.customerPhone(),notes,total,idempotencyKey,requestHash);
  for(ValidatedOrderLine line:lines)dao.createOrderItem(table.tenantId(),orderId,line);
  events.publishAfterCommit(RealtimeEvent.orderChanged("ORDER_CREATED",table.tenantId(),table.branchId(),orderId));
  return new OrderReceipt(orderId,orderNumber,table.displayName(),total,"NEW");
 }
}
