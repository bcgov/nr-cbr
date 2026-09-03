# CBR — Crossings and Bridges Register

CBR is the BC Government register of bridges and engineered culverts on forest service and tenured
roads. It covers structure inventory, field inspection, repair and monitor tracking, load-rating
history, and a catalogue of operational reports.

This repository is the modernization of the legacy Java EE application. For what the legacy system
does and how it is built, see **[docs/legacy-cbr-overview.md](./docs/legacy-cbr-overview.md)**.

| Component | Technology |
|-----------|------------|
| Frontend | React 19, TypeScript, Vite, Carbon Design System |
| Backend | Spring Boot 3.5, Java 21, Undertow |
| Database | Oracle — the shared `THE` schema, via the legacy `CBR_*` PL/SQL packages |
| Auth | AWS Cognito (FAM) — IDIR sign-in |

The structure follows [`bcgov/nr-frep`](https://github.com/bcgov/nr-frep): same ministry, same shared
Oracle schema, same WebADE-to-FAM move. Authentication, authorization, and the CSS/design pattern are
ported from it deliberately — when in doubt about a convention, look there first.

## Where things live

**Schema is not in this repo.** CBR's tables, views, the six `CBR_*` PL/SQL packages, the 16 report
procedures, and the role grants are all version-controlled in
[`bcgov-c/nr-mof-db`](https://github.com/bcgov-c/nr-mof-db) under `scripts/THE/`, and deploy from
there with Flyway. There is no local Postgres, no Flyway here, and no DDL.

Business logic stays in the database: the backend does not issue direct table writes. Every call goes
through an Oracle package as `{call CBR_x.PROC(...)}` from a repository extending
`AbstractCbrRepository`, which is the same shape the legacy DAOs already used.

## Run locally

### Prerequisites

- Java 21+ and Maven 3.9+
- Node.js 20+ and npm (CI and the production image use Node 24)
- Access to the Oracle DEV instance (VPN) and the FAM Cognito client details

### Backend

```bash
cd backend
# Fill in the Oracle and Cognito values first — see src/main/resources/application-local.yml
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Listens on **http://localhost:8080**. `/api/**` answers `401` until you sign in; `/actuator/health`
and `/actuator/prometheus` are open.

```bash
mvn test        # unit tests
mvn verify      # tests + coverage
```

### Frontend

```bash
cd frontend
npm ci
npm run dev
```

Serves **http://localhost:3000** and proxies `/api` to the backend.

```bash
npm run typecheck    # tsc -b
npm run test:unit    # vitest, node project
npm run test:browser # vitest, browser project
npm run e2e          # Playwright
npm run lint
```

## Current state

The application shell is in place — FAM authentication, the authorization model, the Carbon layout,
and CI/CD. **The CBR screens have not been built yet.**

Before this can deploy, four things need answering:

1. The OpenShift licence plate and namespaces (`backend/openshift.deploy.yml` carries a `TODO`).
2. The FAM/Cognito client registration.
3. The FAM role set — `RoleConstants` holds a provisional translation of the legacy roles, marked as
   such, and the region-scoping column is still undecided.
4. The repo secrets the workflows reference (`database_*`, `keystore_secret`, `object_storage_*`,
   `oc_namespace`, `oc_token`).

See [docs/legacy-cbr-overview.md §20](./docs/legacy-cbr-overview.md#20-scaffold-migration--what-changed)
for exactly what the scaffold migration changed and what was deliberately left incomplete.

## Contributing

See [CONTRIBUTING.md](./CONTRIBUTING.md) and [SECURITY.md](./SECURITY.md).
