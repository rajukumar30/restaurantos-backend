package com.restaurantos.modules.orders.dto;

import java.time.Instant;

public final class BillReceiptDto {
    private BillReceiptDto() {}
    public record ReceiptLink(String url, Instant expiresAt) {}
    /** Internal representation; only the bytes are exposed in the public PDF response. */
    public record ReceiptDownload(String billNumber, byte[] pdf) {}
}
