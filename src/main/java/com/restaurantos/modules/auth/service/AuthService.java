package com.restaurantos.modules.auth.service;
import com.restaurantos.modules.auth.dto.LoginRequest;
import com.restaurantos.modules.auth.dto.LoginResponse;
public interface AuthService {LoginResponse login(LoginRequest request);}
