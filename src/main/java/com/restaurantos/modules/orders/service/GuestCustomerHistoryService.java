package com.restaurantos.modules.orders.service;
import com.restaurantos.modules.orders.dto.GuestCustomerDto.*;
import java.util.List;
public interface GuestCustomerHistoryService {
 OtpRequested request(String qrToken,PhoneRequest body);
 VerifiedSession verify(String qrToken,VerifyRequest body);
 List<CustomerOrder> orders(String qrToken,String authorization);
}
