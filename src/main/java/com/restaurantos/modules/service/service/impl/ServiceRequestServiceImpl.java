package com.restaurantos.modules.service.service.impl;
import com.restaurantos.modules.service.dto.ServiceRequestDto.*;
import com.restaurantos.modules.service.repository.ServiceRequestDao;
import com.restaurantos.modules.service.service.ServiceRequestService;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.staff.TenantAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
@Service
public class ServiceRequestServiceImpl implements ServiceRequestService {
 private final ServiceRequestDao dao;
 private final TenantAccessService access;
 private final TenantFeatureAccess features;
 public ServiceRequestServiceImpl(ServiceRequestDao dao,TenantAccessService access,TenantFeatureAccess features){
  this.dao=dao;this.access=access;this.features=features;
 }
 private TableInfo table(String qrToken){
  List<TableInfo> tables=dao.tables(qrToken);
  if(tables.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Invalid table QR code");
  return tables.getFirst();
 }
 @Transactional(readOnly=true)
 public boolean availability(String qrToken){
  return features.enabled(table(qrToken).tenantId(),"SERVICE_REQUESTS");
 }
 @Transactional
 public ServiceRequest create(String qrToken,CreateRequest request){
  TableInfo table=table(qrToken);
  features.require(table.tenantId(),"SERVICE_REQUESTS");
  if(!List.of("WAITER","CLEANING","WATER","CUTLERY","BILL","OTHER").contains(request.type()))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported service request");
  if("OTHER".equals(request.type())&&(request.notes()==null||request.notes().isBlank()))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Please describe what you need");
  dao.lockTable(table.id());
  List<ServiceRequest> active=dao.active(table.id(),request.type());
  if(!active.isEmpty())return active.getFirst();
  UUID id=UUID.randomUUID();
  dao.insert(id,table,request);
  return new ServiceRequest(id,request.type(),"REQUESTED",request.notes(),Instant.now(),null);
 }
 @Transactional(readOnly=true)
 public List<ServiceRequest> guestList(String qrToken){
  TableInfo table=table(qrToken);
  features.require(table.tenantId(),"SERVICE_REQUESTS");
  return dao.guestList(table);
 }
 @Transactional
 public ServiceRequest cancel(String qrToken,UUID requestId){
  TableInfo table=table(qrToken);
  features.require(table.tenantId(),"SERVICE_REQUESTS");
  if(dao.cancel(requestId,table)==0)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Request cannot be cancelled");
  return getOne(requestId,table.tenantId(),table.branchId());
 }
 @Transactional(readOnly=true)
 public List<ServiceRequest> staffList(UUID tenantId,UUID branchId,Authentication auth){
  access.requirePermission(auth,tenantId,branchId,"orders:read");
  access.requireBranchInTenant(tenantId,branchId);
  features.require(tenantId,"SERVICE_REQUESTS");
  return dao.staffList(tenantId,branchId);
 }
 @Transactional
 public ServiceRequest update(UUID tenantId,UUID requestId,UUID branchId,UpdateRequest request,Authentication auth){
  UUID staff=access.requirePermission(auth,tenantId,branchId,"orders:manage");
  access.requireBranchInTenant(tenantId,branchId);
  features.require(tenantId,"SERVICE_REQUESTS");
  if(!List.of("ON_THE_WAY","COMPLETED").contains(request.status()))
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid status transition");
  if(dao.update(requestId,tenantId,branchId,request.status(),staff)==0)
   throw new ResponseStatusException(HttpStatus.CONFLICT,"Request status has changed");
  return getOne(requestId,tenantId,branchId);
 }
 private ServiceRequest getOne(UUID id,UUID tenantId,UUID branchId){
  List<ServiceRequest> rows=dao.find(id,tenantId,branchId);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Request not found");
  return rows.getFirst();
 }
}
