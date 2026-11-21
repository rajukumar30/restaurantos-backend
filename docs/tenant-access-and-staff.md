# Sprint 2: tenant access and staff

## Tenant isolation approach

- Every restaurant-owned resource is scoped by `tenant_id`.
- Branch-level access is represented by `user_roles.branch_id`; a NULL branch scope is tenant-wide.
- Staff endpoints resolve the authenticated user from the verified JWT and check active tenant membership, tenant status, role assignment, and the requested permission in the database on every request.
- Branch IDs are validated against the tenant in the route before staff are created.
- The API does not trust a tenant ID or branch ID from the browser without checking the user's membership and role grants.

## Permission keys

The V2 migration seeds the initial permission catalog. Owner roles receive tenant-wide access to all current permissions. The first staff roles are provisioned lazily when a team member is created:

| Role | Initial access |
|---|---|
| MANAGER | Staff read, branch read, menu read/manage, orders read/manage, kitchen, payments read, reports read |
| KITCHEN | Orders read, kitchen operations |
| CASHIER | Orders read, payments read |
| WAITER | Orders read/manage |
| STAFF | Orders read |

These are starter defaults. A role editor and custom permission assignment UI are not part of this milestone.

## API

### List branches visible to the current user

`GET /api/v1/tenants/{tenantId}/branches`

Requires `branch:read`. Tenant-wide owners see all active branches; branch-scoped users see only branches assigned to their role.

### List staff

`GET /api/v1/tenants/{tenantId}/staff?branchId={branchId}`

Requires `staff:read`. Branch-scoped users must supply a branch ID within their assigned scope. Tenant-wide owners can query without a branch ID.

### Create staff account

`POST /api/v1/tenants/{tenantId}/staff`

Requires `staff:manage` for the requested branch.

```json
{
  "name": "Sam Staff",
  "email": "sam@example.com",
  "phone": "+91 9000000000",
  "temporaryPassword": "change-this-password",
  "branchId": "00000000-0000-0000-0000-000000000000",
  "roleKey": "WAITER"
}
```

Allowed initial roles: `MANAGER`, `KITCHEN`, `CASHIER`, `WAITER`, and `STAFF`.

## Known limitations

- Staff account creation currently provisions a temporary password directly; email invitations, first-login password setup, reset, MFA, lockout and revocation are future work.
- One email can only be used for one app account at this stage; attaching an existing user to another restaurant is not yet exposed.
- Staff creation is branch-scoped to one branch per request.
- The frontend team page is a first operational screen, not the final role administration experience.
