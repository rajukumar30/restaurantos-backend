package com.restaurantos.modules.platform.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class PlatformInvoiceDao {
    private final JdbcTemplate jdbc;
    public PlatformInvoiceDao(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Map<String,Object>> list(UUID tenantId) {
        return jdbc.queryForList("""
            SELECT i.id, i.tenant_id AS "tenantId", t.name AS "restaurantName",
                   i.subscription_id AS "subscriptionId", i.period_start AS "periodStart",
                   i.period_end AS "periodEnd", i.due_on AS "dueOn",
                   i.amount_paise AS "amountPaise", i.currency, i.status,
                   i.created_at AS "createdAt"
            FROM platform_subscription_invoices i JOIN tenants t ON t.id=i.tenant_id
            WHERE (?::uuid IS NULL OR i.tenant_id=?::uuid)
            ORDER BY i.due_on DESC, i.id DESC LIMIT 200
            """, tenantId == null ? null : tenantId.toString(),tenantId == null ? null : tenantId.toString());
    }

    public List<Map<String,Object>> dueCandidates(LocalDate today) {
        return jdbc.queryForList("""
            SELECT s.id, s.starts_on AS "startsOn", s.billing_cycle AS "billingCycle"
            FROM platform_restaurant_subscriptions s
            JOIN platform_subscription_plans p ON p.id=s.plan_id
            WHERE s.status='ACTIVE' AND s.starts_on<=?
              AND (s.ends_on IS NULL OR s.ends_on>=?)
              AND COALESCE(s.custom_price_paise,
                  CASE WHEN s.billing_cycle='MONTHLY' THEN p.monthly_price_paise
                       ELSE p.annual_price_paise END)>0
            ORDER BY s.id
            LIMIT 1001
            """,today,today);
    }

    public Map<String,Object> create(UUID subscriptionId, LocalDate periodStart) {
        // Server-side pricing; caller cannot set the invoice amount or mark it paid.
        // Lock subscription to serialize invoice creation against subscription changes.
        List<Map<String,Object>> subscription=jdbc.queryForList("""
            SELECT s.id, s.tenant_id AS "tenantId", s.billing_cycle AS "billingCycle",
                   s.starts_on AS "startsOn", s.ends_on AS "endsOn",
                   s.free_until AS "freeUntil", s.status,
                   COALESCE(s.custom_price_paise,
                     CASE WHEN s.billing_cycle='MONTHLY' THEN p.monthly_price_paise
                          ELSE p.annual_price_paise END) AS "pricePaise"
            FROM platform_restaurant_subscriptions s
            JOIN platform_subscription_plans p ON p.id=s.plan_id
            WHERE s.id=? FOR UPDATE OF s
            """,subscriptionId);
        if(subscription.isEmpty())throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND,"Subscription not found");
        Map<String,Object> s=subscription.get(0);
        LocalDate starts=(LocalDate)s.get("startsOn");
        LocalDate ends=(LocalDate)s.get("endsOn");
        if(!"ACTIVE".equals(s.get("status")) || periodStart.isBefore(starts)
            || (ends!=null && periodStart.isAfter(ends)))
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,"Subscription is not active for this period");
        String cycle=(String)s.get("billingCycle");
        // Period boundaries are anchored to the subscription start date.
        LocalDate cursor=starts;
        for (int n=1;cursor.isBefore(periodStart) && n<=1200;n++) {
            cursor="MONTHLY".equals(cycle)?starts.plusMonths(n):starts.plusYears(n);
        }
        if(periodStart.isAfter(LocalDate.now(java.time.ZoneOffset.UTC)) || !cursor.equals(periodStart))
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,"Period must start on a subscription billing anniversary");
        LocalDate periodEnd=("MONTHLY".equals(cycle)?periodStart.plusMonths(1):periodStart.plusYears(1)).minusDays(1);
        if(ends!=null && periodEnd.isAfter(ends))periodEnd=ends;
        LocalDate freeUntil=(LocalDate)s.get("freeUntil");
        if(freeUntil!=null && !freeUntil.isBefore(periodStart))
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,"Free or partially free billing period requires separate prorating rules");
        long price=((Number)s.get("pricePaise")).longValue();
        if(price==0)throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.CONFLICT,"Free plan does not require a payment invoice");
        jdbc.update("""
            INSERT INTO platform_subscription_invoices
            (tenant_id,subscription_id,period_start,period_end,due_on,amount_paise)
            VALUES(?,?,?,?,?,?)
            ON CONFLICT(subscription_id,period_start,period_end) DO NOTHING
            """,s.get("tenantId"),subscriptionId,periodStart,periodEnd,periodStart,price);
        return jdbc.queryForMap("""
            SELECT id,tenant_id AS "tenantId",subscription_id AS "subscriptionId",
                   period_start AS "periodStart",period_end AS "periodEnd",
                   due_on AS "dueOn",amount_paise AS "amountPaise",currency,status
            FROM platform_subscription_invoices
            WHERE subscription_id=? AND period_start=? AND period_end=?
            """,subscriptionId,periodStart,periodEnd);
    }
}
