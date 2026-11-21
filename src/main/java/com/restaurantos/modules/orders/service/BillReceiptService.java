package com.restaurantos.modules.orders.service;

import com.restaurantos.modules.orders.dto.BillReceiptDto.*;
import org.springframework.security.core.Authentication;
import java.util.UUID;

public interface BillReceiptService {
    ReceiptLink issue(UUID tenantId, UUID visitId, UUID branchId, Authentication authentication);
    ReceiptDownload download(String token) throws Exception;
}
