package com.restaurantos.modules.service;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.staff.TenantAccessService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class ServiceRequestController {
    private final JdbcTemplate jdbc;
    private final TenantAccessService access;
    public ServiceRequestController(JdbcTemplate jdbc,TenantAccessService access) {
        this.jdbc=jdbc;this.access=access;
    }

    private TableInfo table(String qrToken) {
        List<TableInfo> tables=jdbc.query("""
            SELECT rt.id,rt.tenant_id,rt.branch_id
            FROM restaurant_tables rt
            JOIN branches b ON b.id=rt.branch_id AND b.tenant_id=rt.tenant_id
            JOIN tenants t ON t.id=rt.tenant_id
            WHERE rt.qr_token=? AND rt.is_active=TRUE
              AND b.status='ACTIVE' AND t.status='ACTIVE'
            """,(rs,i)->new TableInfo(rs.getObject(1,UUID.class),
                rs.getObject(2,UUID.class),rs.getObject(3,UUID.class)),qrToken);
        if(tables.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Invalid table QR code");
        return tables.getFirst();
    }

    @PostMapping("/api/v1/public/qr/{qrToken}/service-requests")
    @Transactional
    public ApiResponse<ServiceRequest> create(@PathVariable String qrToken,@Valid @RequestBody CreateRequest body) {
        TableInfo table=table(qrToken);
        if(!List.of("WAITER","CLEANING","WATER","CUTLERY","BILL","OTHER").contains(body.type()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported service request");
        if("OTHER".equals(body.type()) && (body.notes()==null||body.notes().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Please describe what you need");
        // Lock table to serialize duplicate submissions even from separate browser sessions.
        jdbc.queryForObject("SELECT id FROM restaurant_tables WHERE id=? FOR UPDATE",UUID.class,table.id());
        List<ServiceRequest> active=jdbc.query("""
            SELECT sr.id,sr.request_type,sr.status,sr.notes,sr.created_at,rt.display_name
            FROM service_requests sr JOIN restaurant_tables rt ON rt.id=sr.table_id
            WHERE sr.table_id=? AND sr.request_type=? AND sr.status IN ('REQUESTED','ON_THE_WAY')
            ORDER BY sr.created_at DESC LIMIT 1
            """,ServiceRequestController::map,table.id(),body.type());
        if(!active.isEmpty())return ApiResponse.success("Request already active",active.getFirst());
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO service_requests(id,tenant_id,branch_id,table_id,request_type,notes)
            VALUES(?,?,?,?,?,?)
            """,id,table.tenantId(),table.branchId(),table.id(),body.type(),
                body.notes()==null||body.notes().isBlank()?null:body.notes().trim());
        return ApiResponse.success("Staff has been notified",
            new ServiceRequest(id,body.type(),"REQUESTED",body.notes(),Instant.now(),null));
    }

    @GetMapping("/api/v1/public/qr/{qrToken}/service-requests")
    public ApiResponse<List<ServiceRequest>> guestList(@PathVariable String qrToken) {
        TableInfo table=table(qrToken);
        // QR-based access: intentionally scoped to active requests for the scanned table.
        return ApiResponse.success("Active service requests",jdbc.query("""
            SELECT sr.id,sr.request_type,sr.status,sr.notes,sr.created_at,rt.display_name
            FROM service_requests sr JOIN restaurant_tables rt ON rt.id=sr.table_id
            WHERE sr.table_id=? AND sr.tenant_id=? AND sr.branch_id=?
              AND sr.status IN ('REQUESTED','ON_THE_WAY')
            ORDER BY sr.created_at DESC
            """,ServiceRequestController::map,table.id(),table.tenantId(),table.branchId()));
    }

    @PatchMapping("/api/v1/public/qr/{qrToken}/service-requests/{requestId}/cancel")
    @Transactional
    public ApiResponse<ServiceRequest> cancel(@PathVariable String qrToken,@PathVariable UUID requestId) {
        TableInfo table=table(qrToken);
        int changed=jdbc.update("""
            UPDATE service_requests SET status='CANCELLED',cancelled_at=NOW()
            WHERE id=? AND table_id=? AND tenant_id=? AND branch_id=? AND status='REQUESTED'
            """,requestId,table.id(),table.tenantId(),table.branchId());
        if(changed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Request cannot be cancelled");
        return ApiResponse.success("Request cancelled",getOne(requestId,table.tenantId(),table.branchId()));
    }

    @GetMapping("/api/v1/tenants/{tenantId}/service-requests")
    public ApiResponse<List<ServiceRequest>> staffList(@PathVariable UUID tenantId,@RequestParam UUID branchId,
            Authentication authentication) {
        access.requirePermission(authentication,tenantId,branchId,"orders:read");
        access.requireBranchInTenant(tenantId,branchId);
        return ApiResponse.success("Service requests",jdbc.query("""
            SELECT sr.id,sr.request_type,sr.status,sr.notes,sr.created_at,rt.display_name
            FROM service_requests sr JOIN restaurant_tables rt ON rt.id=sr.table_id
            WHERE sr.tenant_id=? AND sr.branch_id=? AND sr.status IN ('REQUESTED','ON_THE_WAY')
            ORDER BY sr.created_at ASC
            """,ServiceRequestController::map,tenantId,branchId));
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/service-requests/{requestId}")
    @Transactional
    public ApiResponse<ServiceRequest> update(@PathVariable UUID tenantId,@PathVariable UUID requestId,
            @RequestParam UUID branchId,@Valid @RequestBody UpdateRequest body,Authentication authentication) {
        UUID staff=access.requirePermission(authentication,tenantId,branchId,"orders:manage");
        access.requireBranchInTenant(tenantId,branchId);
        int changed;
        if("ON_THE_WAY".equals(body.status())) {
            changed=jdbc.update("""
                UPDATE service_requests SET status='ON_THE_WAY',accepted_at=NOW(),assigned_staff_id=?
                WHERE id=? AND tenant_id=? AND branch_id=? AND status='REQUESTED'
                """,staff,requestId,tenantId,branchId);
        } else if("COMPLETED".equals(body.status())) {
            changed=jdbc.update("""
                UPDATE service_requests SET status='COMPLETED',completed_at=NOW()
                WHERE id=? AND tenant_id=? AND branch_id=? AND status='ON_THE_WAY'
                """,requestId,tenantId,branchId);
        } else throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid status transition");
        if(changed==0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Request status has changed");
        return ApiResponse.success("Service request updated",getOne(requestId,tenantId,branchId));
    }

    private ServiceRequest getOne(UUID id,UUID tenantId,UUID branchId) {
        List<ServiceRequest> rows=jdbc.query("""
            SELECT sr.id,sr.request_type,sr.status,sr.notes,sr.created_at,rt.display_name
            FROM service_requests sr JOIN restaurant_tables rt ON rt.id=sr.table_id
            WHERE sr.id=? AND sr.tenant_id=? AND sr.branch_id=?
            """,ServiceRequestController::map,id,tenantId,branchId);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Request not found");
        return rows.getFirst();
    }
    private static ServiceRequest map(java.sql.ResultSet rs,int row)throws java.sql.SQLException {
        return new ServiceRequest(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),
            rs.getString(4),rs.getTimestamp(5).toInstant(),rs.getString(6));
    }
    private record TableInfo(UUID id,UUID tenantId,UUID branchId){}
    public record CreateRequest(@NotNull String type,@Size(max=250) String notes){}
    public record UpdateRequest(@NotNull String status){}
    public record ServiceRequest(UUID id,String type,String status,String notes,Instant createdAt,String tableName){}
}
