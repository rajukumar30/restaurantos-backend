package com.restaurantos.modules.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class PlatformInvoiceEmailDao {
    private final JdbcTemplate jdbc;
    public PlatformInvoiceEmailDao(JdbcTemplate jdbc){this.jdbc=jdbc;}

    /** Aggregate delivery states without exposing email addresses or credentials. */
    public List<Map<String,Object>> deliverySummary(){
        return jdbc.queryForList("""
            SELECT status,COUNT(*) AS "count"
            FROM platform_invoice_reminder_emails
            GROUP BY status ORDER BY status
            """);
    }

    /** Recent deliveries for platform administrators; no recipient addresses returned. */
    public List<Map<String,Object>> recentDeliveries(){
        return jdbc.queryForList("""
            SELECT e.id,e.status,e.attempts,e.created_at AS "createdAt",
                   e.sent_at AS "sentAt",e.next_attempt_at AS "nextAttemptAt",
                   e.last_error AS "lastError",t.name AS "restaurantName",
                   r.reminder_type AS "reminderType",i.due_on AS "dueOn"
            FROM platform_invoice_reminder_emails e
            JOIN platform_invoice_reminders r ON r.id=e.reminder_id
            JOIN platform_subscription_invoices i ON i.id=r.invoice_id
            JOIN tenants t ON t.id=i.tenant_id
            ORDER BY e.created_at DESC,e.id DESC LIMIT 50
            """);
    }

    /** Queue only current active owners, never restaurant staff or unrelated tenants. */
    public int enqueue(){
        return jdbc.update("""
            INSERT INTO platform_invoice_reminder_emails(reminder_id,recipient_email)
            SELECT DISTINCT r.id,lower(trim(u.email))
            FROM platform_invoice_reminders r
            JOIN platform_subscription_invoices i ON i.id=r.invoice_id AND i.tenant_id=r.tenant_id
            JOIN tenant_users tu ON tu.tenant_id=r.tenant_id AND tu.status='ACTIVE'
            JOIN app_users u ON u.id=tu.user_id AND u.status='ACTIVE'
            JOIN user_roles ur ON ur.tenant_id=tu.tenant_id AND ur.tenant_user_id=tu.id
            JOIN roles role ON role.id=ur.role_id
            WHERE i.status='UNPAID'
              AND upper(role.role_key) IN ('OWNER','RESTAURANT_OWNER')
              AND u.email IS NOT NULL AND trim(u.email) <> ''
              AND r.scheduled_on >= CURRENT_DATE - 14
            ON CONFLICT(reminder_id,recipient_email) DO NOTHING
            """);
    }

    /** Atomic claim with a lease. Retry after a crash may resend: SMTP cannot guarantee exactly-once. */
    public List<Map<String,Object>> claim(int batch){
        return jdbc.queryForList("""
            WITH picked AS (
              SELECT e.id FROM platform_invoice_reminder_emails e
              JOIN platform_invoice_reminders r ON r.id=e.reminder_id
              JOIN platform_subscription_invoices i ON i.id=r.invoice_id
              WHERE (e.status IN ('PENDING','FAILED') OR
                     (e.status='SENDING' AND e.lease_until < NOW()))
                AND e.next_attempt_at<=NOW() AND e.attempts<5
                AND i.status='UNPAID'
              ORDER BY e.next_attempt_at,e.id LIMIT ?
              FOR UPDATE OF e SKIP LOCKED
            )
            UPDATE platform_invoice_reminder_emails e
            SET status='SENDING',attempts=e.attempts+1,
                lease_until=NOW()+INTERVAL '5 minutes',updated_at=NOW()
            FROM picked p WHERE e.id=p.id
            RETURNING e.id
            """,batch);
    }

    public Map<String,Object> details(UUID id){
        return jdbc.queryForMap("""
            SELECT e.id,e.recipient_email AS "recipientEmail",e.attempts,
                   r.reminder_type AS "reminderType",i.amount_paise AS "amountPaise",
                   i.due_on AS "dueOn",i.status,t.name AS "restaurantName"
            FROM platform_invoice_reminder_emails e
            JOIN platform_invoice_reminders r ON r.id=e.reminder_id
            JOIN platform_subscription_invoices i ON i.id=r.invoice_id
            JOIN tenants t ON t.id=i.tenant_id
            WHERE e.id=?
            """,id);
    }

    public void sent(UUID id){
        jdbc.update("""
            UPDATE platform_invoice_reminder_emails SET status='SENT',sent_at=NOW(),
            lease_until=NULL,last_error=NULL,updated_at=NOW()
            WHERE id=? AND status='SENDING'
            """,id);
    }
    public void failed(UUID id,String message){
        jdbc.update("""
            UPDATE platform_invoice_reminder_emails SET status='FAILED',
            lease_until=NULL,last_error=?,updated_at=NOW(),
            next_attempt_at=NOW()+(INTERVAL '5 minutes' * POWER(2,LEAST(attempts-1,4)))
            WHERE id=? AND status='SENDING'
            """,message.length()>500?message.substring(0,500):message,id);
    }
}
