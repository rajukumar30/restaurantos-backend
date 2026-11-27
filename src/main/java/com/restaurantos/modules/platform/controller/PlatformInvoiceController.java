package com.restaurantos.modules.platform.controller;

import com.restaurantos.modules.platform.service.PlatformInvoiceService;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform/invoices")
public class PlatformInvoiceController {
    private final PlatformInvoiceService service;
    public PlatformInvoiceController(PlatformInvoiceService service) { this.service=service; }
    public record IssueRequest(UUID subscriptionId,LocalDate periodStart) {}
    @GetMapping
    public Map<String,Object> list(@RequestParam(required=false) UUID tenantId) {
        return Map.of("success",true,"data",service.list(tenantId));
    }
    @PostMapping
    public Map<String,Object> issue(@RequestBody IssueRequest request) {
        if(request==null)throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST,"Invoice request required");
        return Map.of("success",true,"data",service.issue(request.subscriptionId(),request.periodStart()));
    }
}
