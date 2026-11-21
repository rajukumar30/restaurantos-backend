package com.restaurantos.modules.tables.service.impl;
import com.restaurantos.modules.tables.dto.TableDto.*;
import com.restaurantos.modules.tables.repository.TableDao;
import com.restaurantos.modules.tables.service.TableManagementService;
import com.restaurantos.modules.staff.TenantAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.SecureRandom;
import java.util.*;
@Service
public class TableManagementServiceImpl implements TableManagementService {
 private final TableDao dao;
 private final TenantAccessService access;
 private final SecureRandom random=new SecureRandom();
 public TableManagementServiceImpl(TableDao dao,TenantAccessService access){this.dao=dao;this.access=access;}
 private void authorize(Authentication auth,UUID tenantId,UUID branchId,String permission){
  access.requirePermission(auth,tenantId,branchId,permission);
  access.requireBranchInTenant(tenantId,branchId);
 }
 @Transactional(readOnly=true)
 public List<RestaurantTable> list(UUID tenantId,UUID branchId,Authentication auth){
  authorize(auth,tenantId,branchId,"tables:read");
  return dao.list(tenantId,branchId);
 }
 @Transactional
 public RestaurantTable create(UUID tenantId,CreateTableRequest request,Authentication auth){
  authorize(auth,tenantId,request.branchId(),"tables:manage");
  String number=request.tableNumber().trim();
  if(dao.numberExists(tenantId,request.branchId(),number))
   throw new ResponseStatusException(HttpStatus.CONFLICT,"A table with this number already exists in this branch.");
  byte[] bytes=new byte[32];
  random.nextBytes(bytes);
  String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  UUID id=UUID.randomUUID();
  String name=request.displayName()==null||request.displayName().isBlank()?"Table "+number:request.displayName().trim();
  dao.insert(id,tenantId,request.branchId(),number,name,request.seatingCapacity(),token);
  return new RestaurantTable(id,number,name,request.seatingCapacity(),token,true);
 }
 @Transactional
 public RestaurantTable update(UUID tenantId,UUID tableId,UpdateTableRequest request,Authentication auth){
  authorize(auth,tenantId,request.branchId(),"tables:manage");
  if(dao.update(tenantId,request.branchId(),tableId,request)==0)
   throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Table not found in this branch.");
  return dao.find(tenantId,request.branchId(),tableId);
 }
 @Transactional(readOnly=true)
 public PublicTableMenu publicMenu(UUID tenantId,String qrToken){
  PublicTableMenu menu=dao.publicMenu(tenantId,qrToken);
  if(menu==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"This table QR code is invalid or inactive.");
  return menu;
 }
}
