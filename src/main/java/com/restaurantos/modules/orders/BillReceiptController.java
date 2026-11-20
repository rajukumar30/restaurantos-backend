package com.restaurantos.modules.orders;

import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.staff.TenantAccessService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Downloadable paid receipts via opaque 48-hour links, never a WhatsApp PDF attachment. */
@RestController
public class BillReceiptController {
    private final JdbcTemplate jdbc;
    private final TenantAccessService access;
    private final SecureRandom secureRandom = new SecureRandom();
    private final String publicBaseUrl;

    public BillReceiptController(JdbcTemplate jdbc, TenantAccessService access,
        @Value("${restaurantos.receipts.public-base-url:}") String publicBaseUrl) {
        this.jdbc=jdbc;this.access=access;this.publicBaseUrl=publicBaseUrl;
    }

    @PostMapping("/api/v1/tenants/{tenantId}/bills/{visitId}/receipt-link")
    @Transactional
    public ApiResponse<ReceiptLink> issue(@PathVariable UUID tenantId,@PathVariable UUID visitId,
            @RequestParam UUID branchId,Authentication authentication) {
        access.requirePermission(authentication,tenantId,branchId,"orders:manage");
        access.requireBranchInTenant(tenantId,branchId);
        if(publicBaseUrl.isBlank()||!publicBaseUrl.startsWith("https://"))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Public receipt URL not configured");
        List<String> status=jdbc.query("""
            SELECT v.status FROM table_visits v
            WHERE v.id=? AND v.tenant_id=? AND v.branch_id=? FOR UPDATE
            """,(rs,i)->rs.getString(1),visitId,tenantId,branchId);
        if(status.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Bill not found");
        if(!List.of("PAID","CLOSED").contains(status.getFirst()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Receipt available only after confirmed payment");
        Integer confirmed=jdbc.queryForObject("""
            SELECT COUNT(*) FROM table_bill_payments
            WHERE visit_id=? AND tenant_id=? AND status='PAID'
            """,Integer.class,visitId,tenantId);
        if(confirmed==null||confirmed==0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"No confirmed payment found");
        byte[] bytes=new byte[32];secureRandom.nextBytes(bytes);
        String token=HexFormat.of().formatHex(bytes);
        jdbc.update("DELETE FROM bill_receipt_links WHERE visit_id=? AND tenant_id=?",visitId,tenantId);
        jdbc.update("""
            INSERT INTO bill_receipt_links(token_hash,tenant_id,visit_id,expires_at)
            VALUES(?,?,?,NOW()+INTERVAL '48 hours')
            """,hash(token),tenantId,visitId);
        String url=publicBaseUrl.replaceAll("/+$","")+"/api/v1/public/receipts/"+token+".pdf";
        return ApiResponse.success("Secure receipt link created",new ReceiptLink(url,Instant.now().plusSeconds(172800)));
    }

    @GetMapping(value="/api/v1/public/receipts/{token}.pdf",produces=MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> download(@PathVariable String token) throws Exception {
        if(!token.matches("[a-f0-9]{64}"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Receipt not found");
        List<Receipt> receipts=jdbc.query("""
            SELECT v.id,v.bill_number,rt.display_name
            FROM bill_receipt_links l
            JOIN table_visits v ON v.id=l.visit_id AND v.tenant_id=l.tenant_id
            JOIN restaurant_tables rt ON rt.id=v.table_id AND rt.tenant_id=v.tenant_id
            WHERE l.token_hash=? AND l.revoked_at IS NULL AND l.expires_at>NOW()
              AND v.status IN ('PAID','CLOSED')
              AND EXISTS(SELECT 1 FROM table_bill_payments p
                         WHERE p.visit_id=v.id AND p.tenant_id=v.tenant_id AND p.status='PAID')
            """,(rs,i)->new Receipt(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)),hash(token));
        if(receipts.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Receipt unavailable");
        Receipt receipt=receipts.getFirst();
        List<String> items=jdbc.query("""
            SELECT oi.item_name || ' x' || oi.quantity || '  INR ' || oi.line_total
            FROM restaurant_orders o
            JOIN restaurant_order_items oi ON oi.order_id=o.id AND oi.tenant_id=o.tenant_id
            WHERE o.table_visit_id=? AND o.status <> 'CANCELLED'
            ORDER BY o.created_at,oi.created_at
            """,(rs,i)->rs.getString(1),receipt.visitId());
        java.math.BigDecimal total=jdbc.queryForObject("""
            SELECT COALESCE(SUM(total_amount),0) FROM restaurant_orders
            WHERE table_visit_id=? AND status <> 'CANCELLED'
            """,java.math.BigDecimal.class,receipt.visitId());
        byte[] pdf=render(receipt,items,total);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\""+receipt.billNumber()+".pdf\"")
            .header(HttpHeaders.CACHE_CONTROL,"no-store")
            .contentType(MediaType.APPLICATION_PDF).body(pdf);
    }

    private static byte[] render(Receipt receipt,List<String> items,java.math.BigDecimal total) throws Exception {
        try(PDDocument document=new PDDocument();ByteArrayOutputStream output=new ByteArrayOutputStream()){
            PDPage page=new PDPage(PDRectangle.A4);document.addPage(page);
            try(PDPageContentStream stream=new PDPageContentStream(document,page)){
                PDType1Font regular=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                PDType1Font bold=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                float y=790;
                y=write(stream,bold,16,y,"RESTAURANTOS - PAID RECEIPT");
                y=write(stream,regular,11,y,"Bill: "+receipt.billNumber());
                y=write(stream,regular,11,y,"Table: "+receipt.tableName());
                y-=12;
                for(String item:items){
                    if(y<100){y=write(stream,regular,11,y,"Additional items omitted; contact the restaurant.");break;}
                    y=write(stream,regular,10,y,item);
                }
                y-=15;
                write(stream,bold,13,y,"TOTAL PAID: INR "+total);
            }
            document.save(output);
            return output.toByteArray();
        }
    }
    private static float write(PDPageContentStream stream,PDType1Font font,int size,float y,String value)
            throws java.io.IOException {
        String safe=value.replaceAll("[^\\x20-\\x7E]","?");
        if(safe.length()>95)safe=safe.substring(0,92)+"...";
        stream.beginText();stream.setFont(font,size);stream.newLineAtOffset(45,y);
        stream.showText(safe);stream.endText();return y-21;
    }
    private static String hash(String token){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private record Receipt(UUID visitId,String billNumber,String tableName){}
    public record ReceiptLink(String url,Instant expiresAt){}
}
