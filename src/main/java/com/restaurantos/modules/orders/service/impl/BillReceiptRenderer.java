package com.restaurantos.modules.orders.service.impl;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;
import java.io.*;
import java.math.BigDecimal;
import java.util.List;

/** Pure PDF rendering, with no access to persistence, authentication or the network. */
@Component
public class BillReceiptRenderer {
    public byte[] render(String billNumber, String tableName, List<String> items, BigDecimal total) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document,page)) {
                PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                float y = 790;
                y = write(stream,bold,16,y,"RESTAURANTOS - PAID RECEIPT");
                y = write(stream,regular,11,y,"Bill: " + billNumber);
                y = write(stream,regular,11,y,"Table: " + tableName);
                y -= 12;
                for (String item : items) {
                    if (y < 100) {
                        y = write(stream,regular,11,y,"Additional items omitted; contact the restaurant.");
                        break;
                    }
                    y = write(stream,regular,10,y,item);
                }
                y -= 15;
                write(stream,bold,13,y,"TOTAL PAID: INR " + total);
            }
            document.save(output);
            return output.toByteArray();
        }
    }
    private float write(PDPageContentStream stream, PDType1Font font, int size, float y, String value)
        throws IOException {
        String safe = value.replaceAll("[^\\x20-\\x7E]", "?");
        if (safe.length() > 95) safe = safe.substring(0,92) + "...";
        stream.beginText();
        stream.setFont(font,size);
        stream.newLineAtOffset(45,y);
        stream.showText(safe);
        stream.endText();
        return y - 21;
    }
}
