package com.restaurantos.modules.orders.service.impl;
import com.restaurantos.modules.orders.dto.TableBillDto.*;
import com.restaurantos.modules.orders.repository.TableBillDao;
import com.restaurantos.modules.orders.service.TableBillService;
import com.restaurantos.modules.staff.TenantAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@Service
public class TableBillServiceImpl implements TableBillService {
 private final TableBillDao dao;
 private final TenantAccessService access;
 public TableBillServiceImpl(TableBillDao dao,TenantAccessService access){this.dao=dao;this.access=access;}
 private void authorize(UUID tenantId,UUID branchId,Authentication auth){
  access.requirePermission(auth,tenantId,branchId,"orders:read");
  access.requireBranchInTenant(tenantId,branchId);
 }
 @Transactional(readOnly=true)
 public List<BillSummary> active(UUID tenantId,UUID branchId,Authentication auth){
  authorize(tenantId,branchId,auth);
  return dao.active(tenantId,branchId);
 }
 @Transactional(readOnly=true)
 public BillDetail detail(UUID tenantId,UUID branchId,UUID visitId,Authentication auth){
  authorize(tenantId,branchId,auth);
  List<BillSummary> summaries=dao.summary(tenantId,branchId,visitId);
  if(summaries.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Bill not found");
  return new BillDetail(summaries.getFirst(),dao.lines(tenantId,branchId,visitId));
 }
}
