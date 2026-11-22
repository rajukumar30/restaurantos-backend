package com.restaurantos.modules.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class PlatformInvoiceReminderDao {
    private final JdbcTemplate jdbc;
    public PlatformInvoiceReminderDao(JdbcTemplate jdbc){this.jdbc=jdbc;}

    /** Database-atomic deduplication; safe to retry on another server instance. */
    public int createDueReminders(LocalDate today){
        return jdbc.update("""
            INSERT INTO platform_invoice_reminders(invoice_id,tenant_id,reminder_type,scheduled_on)
            SELECT i.id,i.tenant_id,
                CASE (i.due_on - ?::date)
                    WHEN 7 THEN 'DUE_7_DAYS'
                    WHEN 3 THEN 'DUE_3_DAYS'
                    WHEN 0 THEN 'DUE_TODAY'
                    WHEN -1 THEN 'OVERDUE_1_DAY'
                    WHEN -3 THEN 'OVERDUE_3_DAYS'
                END,?::date
            FROM platform_subscription_invoices i
            WHERE i.status='UNPAID'
              AND (i.due_on - ?::date) IN (7,3,0,-1,-3)
            ON CONFLICT(invoice_id,reminder_type) DO NOTHING
            """,today,today,today);
    }

    public List<Map<String,Object>> forTenant(UUID tenantId){
        return jdbc.queryForList("""
            SELECT r.id,r.invoice_id AS "invoiceId",r.reminder_type AS "reminderType",
                   r.scheduled_on AS "scheduledOn",r.created_at AS "createdAt",
                   i.due_on AS "dueOn",i.amount_paise AS "amountPaise",i.status
            FROM platform_invoice_reminders r
            JOIN platform_subscription_invoices i ON i.id=r.invoice_id
            WHERE r.tenant_id=? AND i.tenant_id=?
            ORDER BY r.created_at DESC,r.id DESC LIMIT 100
            """,tenantId,tenantId);
    }
}
