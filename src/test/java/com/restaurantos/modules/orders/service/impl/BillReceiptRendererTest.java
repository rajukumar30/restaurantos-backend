package com.restaurantos.modules.orders.service.impl;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class BillReceiptRendererTest {
    @Test void rendersEveryItemAcrossMultiplePages() throws Exception {
        var items = IntStream.range(0, 100).mapToObj(i -> "Unique receipt item " + i).toList();
        byte[] pdf = new BillReceiptRenderer().render("BILL-123", "Table 1", items, new BigDecimal("999.00"));
        try (var document = Loader.loadPDF(pdf)) {
            assertTrue(document.getNumberOfPages() > 1);
            var stripper = new org.apache.pdfbox.text.PDFTextStripper();
            String text = stripper.getText(document);
            assertTrue(text.contains("Unique receipt item 0"));
            assertTrue(text.contains("Unique receipt item 99"));
            assertTrue(text.contains("TOTAL PAID: INR 999.00"));
        }
    }
}
