package com.restaurantos.modules.orders;

import com.restaurantos.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/qr/{qrToken}/customer")
public class GuestCustomerHistoryController {
    private static final String DEMO_CODE = "123456";
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    private final boolean demoEnabled;

    public GuestCustomerHistoryController(JdbcTemplate jdbc,
            @Value("${restaurantos.customer-auth.demo-otp-enabled:false}") boolean demoEnabled) {
        this.jdbc = jdbc;
        this.demoEnabled = demoEnabled;
    }

    private UUID tenant(String qrToken) {
        List<UUID> ids = jdbc.query("""
            SELECT rt.tenant_id FROM restaurant_tables rt
            JOIN branches b ON b.id=rt.branch_id AND b.tenant_id=rt.tenant_id
            JOIN tenants t ON t.id=rt.tenant_id
            WHERE rt.qr_token=? AND rt.is_active=TRUE AND b.status='ACTIVE' AND t.status='ACTIVE'
            """, (rs, i) -> rs.getObject(1, UUID.class), qrToken);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid restaurant QR");
        return ids.getFirst();
    }

    private void requireDemo() {
        if (!demoEnabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "SMS verification is not configured");
    }

    @PostMapping("/otp/request")
    @Transactional
    public ApiResponse<OtpRequested> request(@PathVariable String qrToken, @Valid @RequestBody PhoneRequest body) {
        requireDemo();
        UUID tenantId = tenant(qrToken);
        Integer count = jdbc.queryForObject("""
            SELECT COUNT(*) FROM guest_otp_challenges
            WHERE tenant_id=? AND phone=? AND created_at > NOW()-INTERVAL '1 hour'
            """, Integer.class, tenantId, body.phone());
        if (count != null && count >= 3)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Try again in an hour");
        jdbc.update("""
            UPDATE guest_otp_challenges SET consumed_at=NOW()
            WHERE tenant_id=? AND phone=? AND consumed_at IS NULL
            """, tenantId, body.phone());
        UUID challengeId = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO guest_otp_challenges(id,tenant_id,phone,expires_at)
            VALUES(?,?,?,NOW()+INTERVAL '5 minutes')
            """, challengeId, tenantId, body.phone());
        return ApiResponse.success("Development verification challenge created",
                new OtpRequested(challengeId, 300, DEMO_CODE));
    }

    @PostMapping("/otp/verify")
    public ApiResponse<VerifiedSession> verify(@PathVariable String qrToken,
            @Valid @RequestBody VerifyRequest body) {
        requireDemo();
        UUID tenantId = tenant(qrToken);
        List<String> phones = jdbc.query("""
            UPDATE guest_otp_challenges SET attempts=attempts+1
            WHERE id=? AND tenant_id=? AND consumed_at IS NULL AND expires_at>NOW() AND attempts<5
            RETURNING phone
            """, (rs, i) -> rs.getString(1), body.challengeId(), tenantId);
        if (phones.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Challenge expired or invalid");
        if (!MessageDigest.isEqual(DEMO_CODE.getBytes(StandardCharsets.UTF_8),
                body.code().getBytes(StandardCharsets.UTF_8)))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect verification code");
        jdbc.update("UPDATE guest_otp_challenges SET consumed_at=NOW() WHERE id=?", body.challengeId());
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = HexFormat.of().formatHex(secret);
        jdbc.update("""
            INSERT INTO guest_customer_sessions(token_hash,tenant_id,phone,expires_at)
            VALUES(?,?,?,NOW()+INTERVAL '7 days')
            """, sha256(token), tenantId, phones.getFirst());
        return ApiResponse.success("Phone verified",
                new VerifiedSession(token, phones.getFirst(), Instant.now().plusSeconds(604800)));
    }

    @GetMapping("/orders")
    public ApiResponse<List<CustomerOrder>> orders(@PathVariable String qrToken,
            @RequestHeader(value="Authorization", required=false) String authorization) {
        UUID tenantId = tenant(qrToken);
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Verification required");
        String token = authorization.substring(7);
        if (!token.matches("[a-f0-9]{64}"))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        List<String> phones = jdbc.query("""
            SELECT phone FROM guest_customer_sessions
            WHERE token_hash=? AND tenant_id=? AND expires_at>NOW()
            """, (rs, i) -> rs.getString(1), sha256(token), tenantId);
        if (phones.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired");
        List<CustomerOrder> result = jdbc.query("""
            SELECT o.id,o.order_number,o.status,o.total_amount,o.created_at,rt.display_name
            FROM restaurant_orders o
            JOIN restaurant_tables rt ON rt.id=o.table_id AND rt.tenant_id=o.tenant_id
            WHERE o.tenant_id=? AND o.customer_phone=?
            ORDER BY o.created_at DESC LIMIT 100
            """, (rs, i) -> new CustomerOrder(rs.getObject(1,UUID.class), rs.getString(2),
                rs.getString(3),rs.getBigDecimal(4),rs.getTimestamp(5).toInstant(),rs.getString(6)),
                tenantId, phones.getFirst());
        return ApiResponse.success("Verified customer history", result);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record PhoneRequest(@Pattern(regexp="\\+91[6-9][0-9]{9}") String phone) {}
    public record VerifyRequest(UUID challengeId, @Pattern(regexp="[0-9]{6}") String code) {}
    public record OtpRequested(UUID challengeId, int expiresInSeconds, String demoCode) {}
    public record VerifiedSession(String accessToken, String phone, Instant expiresAt) {}
    public record CustomerOrder(UUID orderId, String orderNumber, String status,
            java.math.BigDecimal totalAmount, Instant createdAt, String tableName) {}
}
