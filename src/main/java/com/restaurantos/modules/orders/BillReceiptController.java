package com.restaurantos.modules.orders;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.orders.dto.BillReceiptDto.*;
import com.restaurantos.modules.orders.service.BillReceiptService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
public class BillReceiptController {
    private final BillReceiptService service;
    public BillReceiptController(BillReceiptService service) { this.service = service; }

    @PostMapping("/api/v1/tenants/{tenantId}/bills/{visitId}/receipt-link")
    public ApiResponse<ReceiptLink> issue(@PathVariable UUID tenantId, @PathVariable UUID visitId,
                                          @RequestParam UUID branchId, Authentication authentication) {
        return ApiResponse.success("Secure receipt link created",
            service.issue(tenantId, visitId, branchId, authentication));
    }

    @GetMapping(value="/api/v1/public/receipts/{token}.pdf", produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> download(@PathVariable String token) throws Exception {
        ReceiptDownload receipt = service.download(token);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + receipt.billNumber().replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .contentType(MediaType.APPLICATION_PDF)
            .body(receipt.pdf());
    }
}
