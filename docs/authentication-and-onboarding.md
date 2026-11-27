# Authentication and restaurant onboarding

## Environment

The backend reads the following environment variables:

| Variable | Required | Description |
|---|---:|---|
| `DATABASE_URL` | Yes | JDBC PostgreSQL URL |
| `DATABASE_USERNAME` | Yes | Database user |
| `DATABASE_PASSWORD` | Yes | Database password |
| `JWT_SECRET` | Yes in production | Random secret of at least 32 bytes |
| `JWT_EXPIRATION_MINUTES` | No | Access-token lifetime; defaults to 60 |
| `PLATFORM_ADMIN_EMAIL` | For first platform admin | Initial platform administrator email |
| `PLATFORM_ADMIN_PASSWORD` | For first platform admin | Initial password; at least 12 characters |
| `PLATFORM_ADMIN_NAME` | No | Initial administrator display name |
| `CORS_ALLOWED_ORIGINS` | No | Comma-separated frontend origins; defaults to localhost:3000 |

Do not use the development JWT fallback in production. Set a cryptographically random secret in your deployment environment. Do not commit secrets to Git.

## First-time setup

1. Create a PostgreSQL database and configure the database variables.
2. Set `PLATFORM_ADMIN_EMAIL` and `PLATFORM_ADMIN_PASSWORD` before starting the backend for the first time.
3. Start the service. Flyway applies the tenant foundation migration.
4. Open the frontend login page and sign in with the configured platform admin credentials.
5. Use the platform onboarding page to create the first restaurant, its main branch and its owner account.

The bootstrap runner creates the platform admin only when the configured email is not already a platform admin. It does not expose a public admin-registration endpoint.

## API

### Sign in

`POST /api/v1/auth/login`

```json
{
  "email": "admin@example.com",
  "password": "a-long-secret-password"
}
```

Returns a bearer access token and the user's authorities. Send it on protected requests as `Authorization: Bearer <token>`.

### Create a restaurant

`POST /api/v1/platform/restaurants`

Requires the `PLATFORM_ADMIN` authority.

```json
{
  "restaurantName": "The Green Table",
  "branchName": "Main branch",
  "ownerName": "Restaurant Owner",
  "ownerEmail": "owner@example.com",
  "ownerPassword": "a-long-owner-password",
  "ownerPhone": "+91 9000000000",
  "branchPhone": "+91 9111111111",
  "addressJson": "{\"line1\":\"Sector 62\",\"city\":\"Noida\"}"
}
```

This operation creates a tenant, its first branch, an owner user, tenant and branch memberships, and a tenant-scoped OWNER role in one database transaction.

## Current scope and security notes

- Login currently issues signed JWT access tokens. Refresh-token rotation, password reset, email verification, rate limiting, audit events and account recovery are not implemented yet.
- Tenant users can sign in, but restaurant-facing APIs and fine-grained role/permission enforcement are the next implementation step.
- The frontend hides platform onboarding from non-admin users, but backend authorization is the actual security boundary.
- The initial owner password is provisioned by the platform administrator. A secure invite/password-setup flow should replace this for production onboarding.
- Review CORS origins, HTTPS, JWT secret management, database backups and operational monitoring before production deployment.
