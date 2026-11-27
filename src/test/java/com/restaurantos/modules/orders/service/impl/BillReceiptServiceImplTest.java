package com.restaurantos.modules.orders.service.impl;

import com.restaurantos.modules.orders.repository.BillReceiptDao;
import com.restaurantos.modules.orders.repository.BillReceiptDao.ReceiptData;
import com.restaurantos.modules.staff.TenantAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BillReceiptServiceImplTest {
    private final BillReceiptDao dao = mock(BillReceiptDao.class);
    private final BillReceiptRenderer renderer = mock(BillReceiptRenderer.class);
    private final TenantAccessService access = mock(TenantAccessService.class);
    private final BillReceiptServiceImpl service =
        new BillReceiptServiceImpl(dao,renderer,access,"https://example.com/");
    private final UUID tenant = UUID.randomUUID(), visit = UUID.randomUUID(), branch = UUID.randomUUID();
    private final UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(UUID.randomUUID(),null);

    @Test void cannotIssueWithoutPayment() {
        when(dao.lockVisitStatus(tenant,visit,branch)).thenReturn("PAID");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
            () -> service.issue(tenant,visit,branch,auth)).getStatusCode());
        verify(dao,never()).insertLink(any(),any(),any());
    }
    @Test void issuesHashedOpaqueLinkOnlyAfterConfirmation() {
        when(dao.lockVisitStatus(tenant,visit,branch)).thenReturn("PAID");
        when(dao.hasConfirmedPayment(tenant,visit)).thenReturn(true);
        var link = service.issue(tenant,visit,branch,auth);
        assertTrue(link.url().matches("https://example.com/api/v1/public/receipts/[a-f0-9]{64}\\.pdf"));
        verify(access).requirePermission(auth,tenant,branch,"payments:read");
        verify(dao).deleteExistingLinks(tenant,visit);
        verify(dao).insertLink(argThat(hash -> hash.matches("[a-f0-9]{64}") &&
            !link.url().contains(hash)),eq(tenant),eq(visit));
    }
    @Test void rejectsMalformedTokenBeforeQuery() {
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
            () -> service.download("bad")).getStatusCode());
        verifyNoInteractions(dao,renderer);
    }
    @Test void rendersOnlyAuthorizedPaidReceipts() throws Exception {
        when(dao.findValidReceipt(anyString())).thenReturn(new ReceiptData(visit,"B-1","Table 1"));
        when(dao.items(visit)).thenReturn(List.of("Dosa x1 INR 100"));
        when(dao.total(visit)).thenReturn(new BigDecimal("100"));
        when(renderer.render(anyString(),anyString(),anyList(),any())).thenReturn(new byte[]{1,2,3});
        var download = service.download("a".repeat(64));
        assertEquals("B-1",download.billNumber());
        assertArrayEquals(new byte[]{1,2,3},download.pdf());
        verify(renderer).render(eq("B-1"),eq("Table 1"),anyList(),eq(new BigDecimal("100")));
    }
}
