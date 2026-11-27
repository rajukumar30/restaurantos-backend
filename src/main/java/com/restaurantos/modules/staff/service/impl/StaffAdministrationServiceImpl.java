package com.restaurantos.modules.staff.service.impl;

import com.restaurantos.modules.staff.TenantAccessService;
import com.restaurantos.modules.staff.dto.StaffAdminDto.*;
import com.restaurantos.modules.staff.repository.StaffAdministrationDao;
import com.restaurantos.modules.staff.service.StaffAdministrationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
public class StaffAdministrationServiceImpl implements StaffAdministrationService {
    private static final List<String> ROLES = List.of("MANAGER", "KITCHEN", "CASHIER", "WAITER", "STAFF");
    private final StaffAdministrationDao dao;
    private final PasswordEncoder encoder;
    private final TenantAccessService access;
    public StaffAdministrationServiceImpl(StaffAdministrationDao dao, PasswordEncoder encoder,
                                          TenantAccessService access) {
        this.dao = dao; this.encoder = encoder; this.access = access;
    }

    @Override @Transactional(readOnly = true)
    public List<StaffMember> list(UUID tenantId, UUID branchId, Authentication auth) {
        access.requirePermission(auth, tenantId, branchId, "staff:read");
        boolean tenantWide = branchId == null && access.hasTenantWidePermission(auth, tenantId, "staff:read");
        return dao.list(tenantId, branchId, tenantWide);
    }
    @Override @Transactional
    public StaffCreated invite(UUID tenantId, InviteStaffRequest req, Authentication auth) {
        access.requirePermission(auth, tenantId, req.branchId(), "staff:manage");
        access.requireBranchInTenant(tenantId, req.branchId());
        String roleKey = req.roleKey().trim().toUpperCase(Locale.ROOT);
        if (!ROLES.contains(roleKey)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Unsupported role. Choose MANAGER, KITCHEN, CASHIER, WAITER or STAFF.");
        if ("MANAGER".equals(roleKey) && !access.hasTenantWidePermission(auth, tenantId, "staff:manage")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a tenant owner can assign the manager role.");
        }
        if (dao.emailExists(req.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This email already has an account. Multi-tenant staff invitations are not enabled yet.");
        }
        UUID userId = UUID.randomUUID(), tenantUserId = UUID.randomUUID(), branchUserId = UUID.randomUUID();
        UUID roleId = ensureRole(tenantId, roleKey);
        dao.createUser(userId, req.name().trim(), req.email().toLowerCase(Locale.ROOT),
            req.phone(), encoder.encode(req.temporaryPassword()));
        dao.createTenantUser(tenantUserId, tenantId, userId);
        dao.createBranchUser(branchUserId, tenantId, req.branchId(), tenantUserId);
        dao.assignRole(tenantId, tenantUserId, roleId, req.branchId());
        return new StaffCreated(userId, tenantUserId, req.branchId(), roleKey, "ACTIVE");
    }
    private UUID ensureRole(UUID tenantId, String roleKey) {
        UUID found = dao.findRole(tenantId, roleKey);
        if (found != null) return found;
        UUID roleId = UUID.randomUUID();
        String displayName = switch (roleKey) {
            case "MANAGER" -> "Branch Manager";
            case "KITCHEN" -> "Kitchen Staff";
            case "CASHIER" -> "Cashier";
            case "WAITER" -> "Waiter";
            default -> "Staff";
        };
        dao.createRole(roleId, tenantId, roleKey, displayName);
        List<String> permissions = switch (roleKey) {
            case "MANAGER" -> List.of("staff:read", "staff:manage", "branch:read", "menu:read",
                "menu:manage", "orders:read", "orders:manage", "kitchen:operate", "payments:read", "reports:read","floor:read","floor:manage");
            case "KITCHEN" -> List.of("orders:read", "kitchen:operate");
            case "CASHIER" -> List.of("orders:read", "payments:read");
            case "WAITER" -> List.of("orders:read", "orders:manage","floor:read","floor:manage");
            default -> List.of("orders:read");
        };
        dao.grantPermissions(roleId, permissions);
        return roleId;
    }
    @Override @Transactional
    public StaffMember update(UUID tenantId,UUID userId,UpdateStaffRequest req,Authentication auth){
        UUID actor=access.requirePermission(auth,tenantId,req.branchId(),"staff:manage");
        if(!access.hasTenantWidePermission(auth,tenantId,"staff:manage")||userId.equals(actor))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Only restaurant owners can edit other staff accounts.");
        access.requireBranchInTenant(tenantId,req.branchId());
        String role=req.roleKey().trim().toUpperCase(Locale.ROOT);
        String status=req.accountStatus().trim().toUpperCase(Locale.ROOT);
        if(!ROLES.contains(role)||!Set.of("ACTIVE","INACTIVE").contains(status))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Select a valid staff role and account status.");
        UUID membership=dao.lockEditableMembership(tenantId,req.branchId(),userId).orElseThrow(()->
            new ResponseStatusException(HttpStatus.FORBIDDEN,"This account is protected, shared across workspaces, or not assigned to this branch."));
        UUID roleId=ensureRole(tenantId,role);
        dao.updateStaff(tenantId,req.branchId(),membership,userId,req.name().trim(),req.phone(),status,roleId);
        return dao.list(tenantId,req.branchId(),false).stream().filter(p->p.userId().equals(userId)).findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Staff account could not be refreshed."));
    }
}
