package com.restaurantos.modules.ordersecurity.service;
import com.restaurantos.modules.ordersecurity.dto.OrderSecurityDto.Update;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class OrderSecurityValidationTest {
 @Test void customLimitsValidatedAtApiBoundary(){
  try(var factory=Validation.buildDefaultValidatorFactory()){
   var v=factory.getValidator();var id=UUID.randomUUID();
   assertTrue(v.validate(new Update(id,"EXPRESS",new BigDecimal("10000"),new BigDecimal("50000"),40,true)).isEmpty());
   assertFalse(v.validate(new Update(id,"EXPRESS",new BigDecimal("10000.01"),new BigDecimal("50000"),40,true)).isEmpty());
   assertFalse(v.validate(new Update(id,"EXPRESS",new BigDecimal("1000"),new BigDecimal("50000.01"),40,true)).isEmpty());
   assertFalse(v.validate(new Update(id,"EXPRESS",new BigDecimal("1000"),new BigDecimal("6000"),41,true)).isEmpty());
   assertFalse(v.validate(new Update(id,"EXPRESS",new BigDecimal("0"),new BigDecimal("6000"),15,true)).isEmpty());
   assertFalse(v.validate(new Update(id,"PREPAID",new BigDecimal("1000"),new BigDecimal("6000"),15,true)).isEmpty());
  }
 }
}
