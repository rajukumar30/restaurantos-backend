package com.restaurantos.modules.orders;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.orders.dto.GuestCustomerDto.*;
import com.restaurantos.modules.orders.service.GuestCustomerHistoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/v1/public/qr/{qrToken}/customer")
public class GuestCustomerHistoryController {
 private final GuestCustomerHistoryService service;
 public GuestCustomerHistoryController(GuestCustomerHistoryService service){this.service=service;}
 @PostMapping("/otp/request")
 public ApiResponse<OtpRequested> request(@PathVariable String qrToken,@Valid @RequestBody PhoneRequest body){
  return ApiResponse.success("Development verification challenge created",service.request(qrToken,body));
 }
 @PostMapping("/otp/verify")
 public ApiResponse<VerifiedSession> verify(@PathVariable String qrToken,@Valid @RequestBody VerifyRequest body){
  return ApiResponse.success("Phone verified",service.verify(qrToken,body));
 }
 @GetMapping("/orders")
 public ApiResponse<List<CustomerOrder>> orders(@PathVariable String qrToken,
  @RequestHeader(value="Authorization",required=false) String authorization){
  return ApiResponse.success("Verified customer history",service.orders(qrToken,authorization));
 }
}
