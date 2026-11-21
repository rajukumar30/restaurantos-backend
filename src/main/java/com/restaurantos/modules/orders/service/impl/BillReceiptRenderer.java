package com.restaurantos.modules.orders.service.impl;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;
import java.io.*;
import java.math.BigDecimal;
import java.util.List;

/** Pure PDF rendering; every billed item is included, with page breaks as needed. */
@Component
public class BillReceiptRenderer {
    public byte[] render(String billNumber, String tableName, List<String> items, BigDecimal total) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDPageContentStream stream = newPage(document);
            try {
                float y = 790;
                y = write(stream,bold,16,y,"RESTAURANTOS - PAID RECEIPT");
                y = write(stream,regular,11,y,"Bill: " + billNumber);
                y = write(stream,regular,11,y,"Table: " + tableName);
                y -= 12;
                for (String item : items) {
                    if (y < 100) {
                        stream.close();
                        stream = newPage(document);
                        y = write(stream,bold,12,790,"RESTAURANTOS - RECEIPT CONTINUED");
                        y = write(stream,regular,10,y,"Bill: " + billNumber);
                        y -= 12;
                    }
                    y = write(stream,regular,10,y,item);
                }
                if (y < 90) {
                    stream.close();
                    stream = newPage(document);
                    y = 790;
                }
                y -= 15;
                write(stream,bold,13,y,"TOTAL PAID: INR " + total);
            } finally {
                stream.close();
            }
            document.save(output);
            return output.toByteArray();
        }
    }
    private PDPageContentStream newPage(PDDocument document) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        return new PDPageContentStream(document,page);
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
