package com.restaurantos.modules.orders;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.staff.TenantAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read-only consolidated bill preview for authorized restaurant staff.
 * Never marks payments as successful; gateway confirmation will be added separately.
 */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/bills")
public class TableBillController {
    private final JdbcTemplate jdbc;
    private final TenantAccessService access;

    public TableBillController(JdbcTemplate jdbc, TenantAccessService access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @GetMapping("/active")
    public ApiResponse<List<BillSummary>> active(@PathVariable UUID tenantId,
            @RequestParam UUID branchId, Authentication authentication) {
        access.requirePermission(authentication, tenantId, branchId, "orders:read");
        access.requireBranchInTenant(tenantId, branchId);
        List<BillSummary> bills = jdbc.query("""
            SELECT v.id, v.bill_number, v.status, rt.display_name,
                   v.opened_at, COALESCE(SUM(o.total_amount)
                     FILTER (WHERE o.status <> 'CANCELLED'),0) AS amount,
                   COUNT(o.id) FILTER (WHERE o.status <> 'CANCELLED') AS order_count
            FROM table_visits v
            JOIN restaurant_tables rt ON rt.id=v.table_id AND rt.tenant_id=v.tenant_id
            LEFT JOIN restaurant_orders o ON o.table_visit_id=v.id AND o.tenant_id=v.tenant_id
            WHERE v.tenant_id=? AND v.branch_id=?
              AND v.status IN ('OPEN','PAYMENT_PENDING','PAID')
            GROUP BY v.id,rt.display_name
            ORDER BY v.opened_at DESC
            """, (rs,i)->new BillSummary(rs.getObject(1,UUID.class),rs.getString(2),
                    rs.getString(3),rs.getString(4),rs.getTimestamp(5).toInstant(),
                    rs.getBigDecimal(6),rs.getInt(7)),tenantId,branchId);
        return ApiResponse.success("Active table bills",bills);
    }

    @GetMapping("/{visitId}")
    public ApiResponse<BillDetail> detail(@PathVariable UUID tenantId,
            @PathVariable UUID visitId, @RequestParam UUID branchId,
            Authentication authentication) {
        access.requirePermission(authentication, tenantId, branchId, "orders:read");
        access.requireBranchInTenant(tenantId, branchId);
        List<BillSummary> bills = jdbc.query("""
            SELECT v.id,v.bill_number,v.status,rt.display_name,v.opened_at,
                   COALESCE(SUM(o.total_amount) FILTER (WHERE o.status <> 'CANCELLED'),0),
                   COUNT(o.id) FILTER (WHERE o.status <> 'CANCELLED')
            FROM table_visits v
            JOIN restaurant_tables rt ON rt.id=v.table_id AND rt.tenant_id=v.tenant_id
            LEFT JOIN restaurant_orders o ON o.table_visit_id=v.id AND o.tenant_id=v.tenant_id
            WHERE v.id=? AND v.tenant_id=? AND v.branch_id=?
            GROUP BY v.id,rt.display_name
            """, (rs,i)->new BillSummary(rs.getObject(1,UUID.class),rs.getString(2),
                rs.getString(3),rs.getString(4),rs.getTimestamp(5).toInstant(),
                rs.getBigDecimal(6),rs.getInt(7)),visitId,tenantId,branchId);
        if(bills.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Bill not found");
        List<BillLine> lines=jdbc.query("""
            SELECT o.order_number,oi.item_name,oi.quantity,oi.unit_price,oi.line_total
            FROM restaurant_orders o
            JOIN restaurant_order_items oi ON oi.order_id=o.id AND oi.tenant_id=o.tenant_id
            WHERE o.table_visit_id=? AND o.tenant_id=? AND o.branch_id=? AND o.status <> 'CANCELLED'
            ORDER BY o.created_at,oi.created_at
            """,(rs,i)->new BillLine(rs.getString(1),rs.getString(2),rs.getInt(3),
                rs.getBigDecimal(4),rs.getBigDecimal(5)),visitId,tenantId,branchId);
        return ApiResponse.success("Consolidated bill preview",new BillDetail(bills.get(0),lines));
    }

    public record BillSummary(UUID visitId,String billNumber,String status,String tableName,
            Instant openedAt,BigDecimal totalAmount,int orderCount){}
    public record BillLine(String orderNumber,String itemName,int quantity,
            BigDecimal unitPrice,BigDecimal lineTotal){}
    public record BillDetail(BillSummary summary,List<BillLine> items){}
}
