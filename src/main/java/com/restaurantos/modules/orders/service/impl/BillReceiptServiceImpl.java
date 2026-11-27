package com.restaurantos.modules.orders.service.impl;

import com.restaurantos.modules.orders.dto.BillReceiptDto.*;
import com.restaurantos.modules.orders.repository.BillReceiptDao;
import com.restaurantos.modules.orders.repository.BillReceiptDao.ReceiptData;
import com.restaurantos.modules.orders.service.BillReceiptService;
import com.restaurantos.modules.staff.TenantAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service
public class BillReceiptServiceImpl implements BillReceiptService {
    private final BillReceiptDao dao;
    private final BillReceiptRenderer renderer;
    private final TenantAccessService access;
    private final SecureRandom random = new SecureRandom();
    private final String publicBaseUrl;

    public BillReceiptServiceImpl(BillReceiptDao dao, BillReceiptRenderer renderer, TenantAccessService access,
                                  @Value("${restaurantos.receipts.public-base-url:}") String publicBaseUrl) {
        this.dao = dao; this.renderer = renderer; this.access = access; this.publicBaseUrl = publicBaseUrl;
    }

    @Override @Transactional
    public ReceiptLink issue(UUID tenantId, UUID visitId, UUID branchId, Authentication auth) {
        access.requirePermission(auth, tenantId, branchId, "payments:read");
        access.requireBranchInTenant(tenantId, branchId);
        if (publicBaseUrl.isBlank() || !publicBaseUrl.startsWith("https://"))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Public receipt URL not configured");
        String status = dao.lockVisitStatus(tenantId, visitId, branchId);
        if (status == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found");
        if (!List.of("PAID", "CLOSED").contains(status))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Receipt available only after confirmed payment");
        if (!dao.hasConfirmedPayment(tenantId, visitId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No confirmed payment found");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        dao.deleteExistingLinks(tenantId, visitId);
        dao.insertLink(hash(token), tenantId, visitId);
        String url = publicBaseUrl.replaceAll("/+$", "") + "/api/v1/public/receipts/" + token + ".pdf";
        return new ReceiptLink(url, Instant.now().plusSeconds(172800));
    }

    @Override @Transactional(readOnly = true)
    public ReceiptDownload download(String token) throws Exception {
        if (token == null || !token.matches("[a-f0-9]{64}"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt not found");
        ReceiptData receipt = dao.findValidReceipt(hash(token));
        if (receipt == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Receipt unavailable");
        return new ReceiptDownload(receipt.billNumber(), renderer.render(
            receipt.billNumber(), receipt.tableName(), dao.items(receipt.visitId()),
            dao.total(receipt.visitId())));
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
