package com.restaurantos.modules.auth;
import com.restaurantos.common.api.ApiResponse;
import com.restaurantos.modules.auth.dto.LoginRequest;
import com.restaurantos.modules.auth.dto.LoginResponse;
import com.restaurantos.modules.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
 private final AuthService service;
 public AuthController(AuthService service){this.service=service;}
 @PostMapping("/login")
 public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request){
  return ApiResponse.success("Login successful",service.login(request));
 }
}
