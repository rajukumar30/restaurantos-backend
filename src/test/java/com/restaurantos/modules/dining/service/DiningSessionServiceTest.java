package com.restaurantos.modules.dining.service;
import com.restaurantos.modules.dining.dto.DiningDto.*;
import com.restaurantos.modules.dining.repository.DiningDao;
import com.restaurantos.modules.orders.dto.CheckoutDto.Scope;
import com.restaurantos.modules.platform.TenantFeatureAccess;
import com.restaurantos.modules.staff.TenantAccessService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class DiningSessionServiceTest{
 private final DiningDao dao=mock(DiningDao.class);private final PasswordEncoder encoder=mock(PasswordEncoder.class);
 private final DiningSessionService service=new DiningSessionService(dao,mock(TenantAccessService.class),mock(TenantFeatureAccess.class),encoder);
 private final Scope s=new Scope(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID());
 private final Visit v=new Visit(s,UUID.randomUUID(),"OPEN");
 @Test void permanentQrAloneCannotAuthorizeOrder(){assertThrows(ResponseStatusException.class,()->service.require("qr",null,true,true));verifyNoInteractions(dao);}
 @Test void staleTokenCannotAuthorizeNewVisit(){when(dao.lockQr("qr")).thenReturn(true);assertThrows(ResponseStatusException.class,()->service.require("qr","a".repeat(64),true,true));}
 @Test void wrongCodeCountsAgainstVisitLimit(){when(dao.lockQr("qr")).thenReturn(true);when(dao.active("qr")).thenReturn(Optional.of(new Active(s,"OPEN","hash",Instant.now().plusSeconds(60),0)));assertThrows(ResponseStatusException.class,()->service.join("qr","123456"));verify(dao).fail(s.visit());}
 @Test void lockedCodeCannotBeGuessed(){when(dao.lockQr("qr")).thenReturn(true);when(dao.active("qr")).thenReturn(Optional.of(new Active(s,"OPEN","hash",Instant.now().plusSeconds(60),5)));assertThrows(ResponseStatusException.class,()->service.join("qr","123456"));verifyNoInteractions(encoder);}
 @Test void successfulJoinReturnsOpaqueToken(){when(dao.lockQr("qr")).thenReturn(true);when(dao.active("qr")).thenReturn(Optional.of(new Active(s,"OPEN","hash",Instant.now().plusSeconds(60),0)));when(encoder.matches("123456","hash")).thenReturn(true);var result=service.join("qr","123456");assertEquals(s.visit(),result.visitId());assertTrue(result.token().matches("[0-9a-f]{64}"));verify(dao).insert(any(),eq(s.visit()),eq(DiningSessionService.hash(result.token())),eq(result.expiresAt()));}
 @Test void submissionKeySurvivesCodeRotationButNotNewVisit(){String key="1234567890123456";assertEquals(service.orderKey(v,key),service.orderKey(v,key));assertEquals(service.orderKey(v,key),service.orderKey(new Visit(s,UUID.randomUUID(),"OPEN"),key));assertNotEquals(service.orderKey(v,key),service.orderKey(new Visit(new Scope(s.tenant(),s.branch(),s.table(),UUID.randomUUID()),UUID.randomUUID(),"OPEN"),key));}
 @Test void largeOrdersAreRejectedAndRetryAllowed(){assertThrows(ResponseStatusException.class,()->service.checkOrderLimits(v,51,1,"key"));when(dao.repeated(v,"key")).thenReturn(true);assertDoesNotThrow(()->service.checkOrderLimits(v,51,1,"key"));}
 @Test void paidVisitCannotOrder(){when(dao.lockQr("qr")).thenReturn(true);when(dao.find("qr",DiningSessionService.hash("a".repeat(64)))).thenReturn(Optional.of(new Visit(s,v.sessionId(),"PAID")));assertThrows(ResponseStatusException.class,()->service.require("qr","a".repeat(64),true,true));}
}
