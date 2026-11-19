# Backend deployment

The API is a Java 21 / Spring Boot service. It can be deployed to any Docker-capable host (for example Render, Railway, Fly.io, or a small VPS).

## Required environment variables

| Variable | Required | Notes |
|---|---:|---|
| `DATABASE_URL` | Yes | JDBC URL, e.g. `jdbc:postgresql://HOST:5432/restaurantos?sslmode=require` |
| `DATABASE_USERNAME` | Yes | PostgreSQL application user |
| `DATABASE_PASSWORD` | Yes | PostgreSQL application password |
| `JWT_SECRET` | Yes | Random secret of at least 32 bytes; do not use the development default |
| `CORS_ALLOWED_ORIGINS` | Yes | Exact deployed frontend origin, e.g. `https://your-frontend.example` |
| `PLATFORM_ADMIN_EMAIL` | First deployment | Initial platform administrator email |
| `PLATFORM_ADMIN_PASSWORD` | First deployment | Initial password; at least 12 characters |
| `PLATFORM_ADMIN_NAME` | No | Defaults to Platform Administrator |
| `PORT` | Host-provided | Defaults to 8080 |
| `JWT_EXPIRATION_MINUTES` | No | Defaults to 60 |

Do not commit production secrets or place them in frontend environment variables.

## Deploy steps

1. Provision a managed PostgreSQL database.
2. Create a database user with permission to create the schema objects and the `pgcrypto` extension used by the initial migration.
3. Create a Docker web service from this repository and select the `chore/bootstrap` branch for preview deployment.
4. Set the environment variables above in the hosting dashboard.
5. Set `PLATFORM_ADMIN_EMAIL` and a strong `PLATFORM_ADMIN_PASSWORD` (12+ characters) for the first deployment so the startup bootstrap creates the first platform administrator. Keep these secrets in the host's secret manager; never commit them.
6. Deploy and wait for Flyway migrations to complete during application startup.
7. Check `/actuator/health` and `/api/v1/health`.
8. Set the frontend's `NEXT_PUBLIC_API_BASE_URL` to the deployed API origin and set backend `CORS_ALLOWED_ORIGINS` to the frontend origin.
9. Test login, tenant/branch permissions, menu, QR lookup, order submission and staff order updates before using real restaurant data.

## Important before production

- Use a strong unique JWT secret and managed PostgreSQL backups.
- Restrict database network access to the application host where supported.
- Configure HTTPS and a custom API domain.
- Confirm that the initial platform administrator and first restaurant onboarding process are provisioned securely before inviting real users.
- Review database migration privileges and retention/backups.
- The QR ordering flow currently supports dine-in table orders. Payment gateway integration, refunds, tax/invoice rules, and live push notifications are not included yet.

## Local Docker build

```bash
docker build -t restaurantos-backend .
docker run --rm -p 8080:8080 \
  -e DATABASE_URL='jdbc:postgresql://host.docker.internal:5432/restaurantos' \
  -e DATABASE_USERNAME='restaurantos' \
  -e DATABASE_PASSWORD='replace-me' \
  -e JWT_SECRET='replace-with-a-random-secret-of-at-least-32-bytes' \
  -e CORS_ALLOWED_ORIGINS='http://localhost:3000' \
  restaurantos-backend
```
