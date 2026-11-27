package com.restaurantos.modules.ordersecurity.service;
import com.restaurantos.modules.ordersecurity.dto.OrderSecurityDto.Settings;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class OrderSecurityServiceTest{
 private Settings mode(String name){return new Settings(name,new BigDecimal("1000"),new BigDecimal("2000"),6);}
 @Test void strictAlwaysReviewsEvenVerified(){assertNotNull(OrderSecurityService.reviewReason(mode("STRICT"),true,BigDecimal.TEN,1,BigDecimal.ZERO));}
 @Test void expressNormalOrderIsImmediate(){assertNull(OrderSecurityService.reviewReason(mode("EXPRESS"),false,new BigDecimal("500"),3,BigDecimal.ZERO));}
 @Test void expressExcessQuantityOrAmountOrCumulativeSpendRequiresReview(){assertNotNull(OrderSecurityService.reviewReason(mode("EXPRESS"),false,BigDecimal.TEN,7,BigDecimal.ZERO));assertNotNull(OrderSecurityService.reviewReason(mode("EXPRESS"),false,new BigDecimal("1001"),1,BigDecimal.ZERO));assertNotNull(OrderSecurityService.reviewReason(mode("EXPRESS"),false,new BigDecimal("501"),1,new BigDecimal("1500")));}
 @Test void verifiedExpressDeviceStillRequiresReviewForLargeOrderButNotCumulativeUnverifiedSpend(){assertNotNull(OrderSecurityService.reviewReason(mode("EXPRESS"),true,new BigDecimal("3000"),10,BigDecimal.ZERO));assertNull(OrderSecurityService.reviewReason(mode("EXPRESS"),true,new BigDecimal("500"),3,new BigDecimal("2000")));}
 @Test void customLimitsAreUsedInsteadOfDefaults(){
  Settings s=new Settings("EXPRESS",new BigDecimal("10000"),new BigDecimal("50000"),40);
  assertNull(OrderSecurityService.reviewReason(s,false,new BigDecimal("10000"),40,new BigDecimal("40000")));
  assertNotNull(OrderSecurityService.reviewReason(s,false,new BigDecimal("10000.01"),40,BigDecimal.ZERO));
  assertNotNull(OrderSecurityService.reviewReason(s,false,new BigDecimal("10000"),40,new BigDecimal("40000.01")));
  assertNotNull(OrderSecurityService.reviewReason(s,true,BigDecimal.TEN,41,BigDecimal.ZERO));
 }
}
