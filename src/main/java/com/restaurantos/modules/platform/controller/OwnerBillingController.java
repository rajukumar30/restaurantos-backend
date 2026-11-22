package com.restaurantos.modules.platform.controller;

import com.restaurantos.modules.auth.repository.AuthDao;
import com.restaurantos.modules.platform.repository.PlatformInvoiceDao;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Read-only billing visibility, with server-side tenant membership enforcement. */
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/billing")
public class OwnerBillingController {
    private final AuthDao auth;
    private final PlatformInvoiceDao invoices;

    public OwnerBillingController(AuthDao auth, PlatformInvoiceDao invoices) {
        this.auth=auth;this.invoices=invoices;
    }

    @GetMapping
    public Map<String,Object> overview(@PathVariable UUID tenantId, Authentication authentication) {
        if(authentication==null || !(authentication.getPrincipal() instanceof UUID userId))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Authentication required");
        boolean owner=auth.roleKeys(userId,tenantId).stream()
            .map(role->role.toUpperCase(Locale.ROOT))
            .anyMatch(role->role.equals("OWNER")||role.equals("RESTAURANT_OWNER"));
        if(!owner && !auth.isPlatformAdmin(userId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only restaurant owners can view billing");
        List<Map<String,Object>> rows=invoices.list(tenantId);
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        long unpaid=rows.stream().filter(r->"UNPAID".equals(r.get("status")))
            .mapToLong(r->((Number)r.get("amountPaise")).longValue()).sum();
        long overdue=rows.stream().filter(r->"UNPAID".equals(r.get("status"))
                && ((LocalDate)r.get("dueOn")).isBefore(today))
            .mapToLong(r->((Number)r.get("amountPaise")).longValue()).sum();
        long dueSoon=rows.stream().filter(r->"UNPAID".equals(r.get("status")))
            .filter(r->{long days=ChronoUnit.DAYS.between(today,(LocalDate)r.get("dueOn"));return days>=0&&days<=7;})
            .count();
        return Map.of("success",true,"data",Map.of(
            "invoices",rows,"unpaidPaise",unpaid,"overduePaise",overdue,
            "dueSoonCount",dueSoon,"asOf",today.toString()));
    }
}
