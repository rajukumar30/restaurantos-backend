package com.restaurantos.modules.orders.service.impl;
import com.restaurantos.modules.orders.dto.OrderDto.*;
import com.restaurantos.modules.orders.entity.*;
import com.restaurantos.modules.orders.repository.OrderPlacementDao;
import com.restaurantos.modules.orders.service.OrderPlacementService;
import com.restaurantos.modules.platform.RestaurantFeatureAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
@Service
public class OrderPlacementServiceImpl implements OrderPlacementService {
 private final OrderPlacementDao dao;
 private final RestaurantFeatureAccessService featureAccess;
 public OrderPlacementServiceImpl(OrderPlacementDao dao,RestaurantFeatureAccessService featureAccess){
  this.dao=dao;this.featureAccess=featureAccess;
 }
 @Override
 @Transactional
 public OrderReceipt place(String qrToken,PlaceOrderRequest request){
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
  dao.lockTable(table.tableId());
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
  dao.createOrder(orderId,orderNumber,table,visitId,request.customerName().trim(),request.customerPhone(),notes,total);
  for(ValidatedOrderLine line:lines)dao.createOrderItem(table.tenantId(),orderId,line);
  return new OrderReceipt(orderId,orderNumber,table.displayName(),total,"NEW");
 }
}
