# RestaurantOS Backend Refactoring — Architecture Review

Branch: `chore/bootstrap` (continuation after `abf93fa6`, Growth Management).

## Scope completed

The remaining Platform Subscriptions, Restaurant Onboarding, Platform Restaurant Directory,
Platform Feature Catalog/Overrides, Staff Administration, and Bill Receipt Generation modules
now follow the same separation as the earlier modules:

`Controller -> Service interface -> @Service implementation -> @Repository DAO -> PostgreSQL`.

* REST route mappings and response messages were retained, with public JSON DTO shapes preserved.
* SQL and JDBC access were extracted into DAO classes.
* Write operations have service-level transactions (subscription replacement, onboarding,
  restaurant/branch updates, feature override updates, staff creation, receipt link issuance).
* New tests cover validation, authorization, dependency ordering, tenant scoping,
  secure receipt issuing and downloading, and onboarding/bootstrap behavior.
* Additional access-layer cleanup moved JWT platform-admin status queries, tenant permission
  queries, subscription feature-gate queries and application bootstrap SQL into DAOs.
* Tenant branch authorization checks and platform audit paging validation now live in services.
* Architecture and route contract tests guard against future regressions.

## Data and security considerations

* Subscription assignments serialize on the tenant row to prevent overlapping active plans.
* Staff operations retain tenant and branch scope checks.
* Receipt tokens remain 32-byte random secrets rendered as 64 lowercase hex characters;
  the database stores their SHA-256 hashes, expires links after 48 hours, and requires
  an existing confirmed payment before issuing or downloading a receipt.
* Public PDF routes retain no-store headers and continue to render paid order items.
* Feature enforcement retains the configured rollout flag and legacy unassigned-tenant policy.

## Verification limits

GitHub Actions runs `mvn -B verify` with Java 21. Unit tests use mocks for DAOs and
authorization collaborators. Green CI proves compilation and the tested application behaviors,
not end-to-end behavior against live Neon production data or Render/Vercel connectivity.

Before production rollout, perform a staging smoke test of onboarding, plan assignment,
feature dependencies, staff permissions, payment/receipt links and actual PDF download.
Deploy only after checking Flyway migrations and environment configuration. Never run
integration smoke tests that mutate real customer data in production.
