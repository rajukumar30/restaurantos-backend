package com.restaurantos.modules.staff.dto;
import com.restaurantos.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;


public final class TenantBranchDto {
 private TenantBranchDto(){}
    public record BranchSummary(UUID id, String name, String branchCode, String address, String phone) {}
}
