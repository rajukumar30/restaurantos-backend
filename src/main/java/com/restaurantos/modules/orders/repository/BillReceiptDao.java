package com.restaurantos.modules.orders.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;

@Repository
public class BillReceiptDao {
    private final JdbcTemplate jdbc;
    public BillReceiptDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record ReceiptData(UUID visitId, String billNumber, String tableName) {}

    public String lockVisitStatus(UUID tenantId, UUID visitId, UUID branchId) {
        return jdbc.query("""
            SELECT v.status FROM table_visits v
            WHERE v.id=? AND v.tenant_id=? AND v.branch_id=? FOR UPDATE
            """, (rs,i) -> rs.getString(1), visitId, tenantId, branchId).stream().findFirst().orElse(null);
    }
    public boolean hasConfirmedPayment(UUID tenantId, UUID visitId) {
        Integer confirmed = jdbc.queryForObject("""
            SELECT COUNT(*) FROM table_bill_payments
            WHERE visit_id=? AND tenant_id=? AND status='PAID'
            """, Integer.class, visitId, tenantId);
        return confirmed != null && confirmed > 0;
    }
    public void deleteExistingLinks(UUID tenantId, UUID visitId) {
        jdbc.update("DELETE FROM bill_receipt_links WHERE visit_id=? AND tenant_id=?", visitId, tenantId);
    }
    public void insertLink(String tokenHash, UUID tenantId, UUID visitId) {
        jdbc.update("""
            INSERT INTO bill_receipt_links(token_hash,tenant_id,visit_id,expires_at)
            VALUES(?,?,?,NOW()+INTERVAL '48 hours')
            """, tokenHash, tenantId, visitId);
    }
    public ReceiptData findValidReceipt(String tokenHash) {
        return jdbc.query("""
            SELECT v.id,v.bill_number,rt.display_name
            FROM bill_receipt_links l
            JOIN table_visits v ON v.id=l.visit_id AND v.tenant_id=l.tenant_id
            JOIN restaurant_tables rt ON rt.id=v.table_id AND rt.tenant_id=v.tenant_id
            WHERE l.token_hash=? AND l.revoked_at IS NULL AND l.expires_at>NOW()
              AND v.status IN ('PAID','CLOSED')
              AND EXISTS(SELECT 1 FROM table_bill_payments p
                         WHERE p.visit_id=v.id AND p.tenant_id=v.tenant_id AND p.status='PAID')
            """, (rs,i) -> new ReceiptData(rs.getObject(1,UUID.class),rs.getString(2),
                rs.getString(3)),tokenHash).stream().findFirst().orElse(null);
    }
    public List<String> items(UUID visitId) {
        return jdbc.query("""
            SELECT oi.item_name || ' x' || oi.quantity || '  INR ' || oi.line_total
            FROM restaurant_orders o
            JOIN restaurant_order_items oi ON oi.order_id=o.id AND oi.tenant_id=o.tenant_id
            WHERE o.table_visit_id=? AND o.status <> 'CANCELLED'
            ORDER BY o.created_at,oi.created_at
            """, (rs,i) -> rs.getString(1), visitId);
    }
    public BigDecimal total(UUID visitId) {
        return jdbc.queryForObject("""
            SELECT COALESCE(SUM(total_amount),0) FROM restaurant_orders
            WHERE table_visit_id=? AND status <> 'CANCELLED'
            """, BigDecimal.class, visitId);
    }
}
