# CLAUDE.md — wms-cashier

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## This Module

Module role: OAuth2 Resource Server — validates JWTs issued by auth-service. Dev port: **8081**.

### Auth Resource Server Config

- Acts as an OAuth2 Resource Server; does not issue tokens.
- Reads `AUTH_ISSUER_URI` env var to discover the JWKS endpoint and validate the `iss` claim.
- Dev default falls back to `http://localhost:9001`.

---

## Project Overview

`flyingjack-cloud` is a multi-module Maven microservices project built on Java 21, Spring Boot 3.2.4, and Spring Cloud 2023.0.1. Services are deployed to Kubernetes.

## Branch Strategy

- **`develop`** is the active development branch. All changes must be made on `develop` — never commit directly to `main`.
- `main` only receives changes via merge from `develop`.
- This rule applies to **all submodules** (`common-lib`, `auth-service`, `third-party-service`, `wms-cashier`) — always verify you are on `develop` before committing in any submodule.
- Before committing in any submodule, run `git branch` to confirm the active branch is `develop`.

## Build & Test Commands

```bash
# Build all modules
./mvnw clean install

# Build with a specific profile (dev is default)
./mvnw clean install -P dev
./mvnw clean install -P beta
./mvnw clean install -P prod

# Run tests for all modules
./mvnw test

# Run tests for a single module
./mvnw test -pl wms-cashier

# Skip tests
./mvnw clean install -DskipTests
```

## Module Structure

Four Maven modules — all are Git submodules except the root:

| Module | Role | Port |
|---|---|---|
| `common-lib` | Shared library (JAR, not a runnable service) | — |
| `auth-service` | OAuth2 Authorization Server (Spring Security OAuth2, JWT, PostgreSQL) | 9001 (dev) |
| `third-party-service` | External integrations: SMS (Alibaba Cloud), Email (Netease SMTP), Captcha | 7100 |
| `wms-cashier` | OAuth2 Resource Server; validates JWTs from auth-service | 8081 |

Git submodules: `common-lib`, `auth-service`, `third-party-service`, `wms-cashier` (all hosted under the `flyingjack-cloud` GitHub org).

## Architecture

### Request Flow
**Production:** Client → Istio IngressGateway (`auth.flyingjack.top`) → auth-service / frontend

**Dev:** Client → auth-service / third-party-service / wms-cashier (direct, no gateway layer)

**Rate limiting must always be implemented at the Istio layer**, not in individual services.

### Service Communication
Services communicate via **OpenFeign**. In K8s (beta/prod), service addresses are resolved via K8s cluster-internal DNS (e.g. `http://thirdparty-service:7100`) — no external service registry. In dev, fixed URLs are configured in `application-dev.yml`.

### Auth Model
- `auth-service` is the OAuth2 Authorization Server that issues JWTs (RSA-signed via JOSE).
- All other services act as OAuth2 Resource Servers, validating tokens issued by auth-service.
- RSA private key injected via environment variable `RSA_PRIVATE_KEY` in non-dev environments.
- **Issuer URI** is controlled by `AUTH_ISSUER_URI` env var (prod: `https://auth.flyingjack.top`). In dev, leave unset — Spring derives it from the request host automatically.
- Resource servers (e.g. `wms-cashier`) read `AUTH_ISSUER_URI` to discover JWKS and validate the `iss` claim. Dev default falls back to `http://localhost:9001`.

### Istio Routing (prod/beta)
`auth.flyingjack.top` is served by a single Istio VirtualService (in `k8s-gitops/auth-service/overlays/prod/networking.yaml`) with the following path rules (priority order):

| Path prefix | Destination | Path rewrite |
|---|---|---|
| `/oauth2/` | auth-service | none |
| `/.well-known/` | auth-service | none |
| `/api/` | auth-service | strip `/api` → `/` |
| `/**` | frontend | none (catch-all, add when ready) |

Business API calls from clients must use the `/api/` prefix in prod. OAuth2 standard endpoints are accessed at their standard paths.

### Configuration
All config is injected as **OS environment variables** via the K8s Pod spec (`env` / `envFrom` referencing ConfigMap or Secret). Services read them through Spring's standard `${ENV_VAR}` placeholder — `spring-cloud-kubernetes-client-config` is not used. Service discovery uses K8s ClusterDNS — Nacos is not used.

Profiles: `dev` (default), `beta`, `prod`. Each service has `application.yml`, `application-{profile}.yml`, and `bootstrap.yml` (bootstrap.yml excluded in dev profile build).

### common-lib Conventions

`common-lib` provides cross-cutting concerns consumed by all services via custom enable-annotations:

- `@EnableGlobalException` — wires `GlobalExceptionHandler` for unified error responses
- `@EnableGlobalI18n` — wires i18n `MessageTool`
- `@EnableGlobalCache` — wires `RedisCacheServiceImpl` with `RedisCacheManager`

**Unified API response:** All endpoints return `ApiRes<T>` with `code`, `message`, `data`, `timestamp`.

## Coding Conventions

### Error Handling
All error codes **must** be defined in `SysErrorCode` (not hardcoded). The workflow:
1. Add the error type to `SysErrorCode`.
2. Add i18n entries for the code in `messages.properties` and locale variants (`messages_en_US.properties`, `messages_zh_CN.properties`).
3. Use the enum via `messageTool.getMessageByContext(SysErrorCode.MY_ERROR.getCode())`.

**Never pass a hardcoded string key directly to `messageTool.getMessageByContext()`.**

### Date/Time
- Use `Instant` for all date/time fields — never `LocalDateTime` or `Date`.
- All times stored as UTC+0 in the database.
- **wms-cashier overrides the root convention**: all timestamps — query/form params, JSON request bodies, and responses — are ISO-8601 strings with a `Z` suffix (e.g. `"2025-01-01T00:00:00Z"`), symmetric in both directions. Do not use bare epoch numbers for new code. See `API.md`'s "Timestamps" section for the full rationale (this was a deliberate fix for a unit-mismatch bug — Jackson silently parsed bare epoch-ms numbers in JSON bodies as epoch-*seconds*) and the documented edge cases (query params still tolerate a bare epoch-ms number for backward compatibility with older clients; JSON bodies do not, and silently misparse one).
- Timezone conversion is the **frontend's** responsibility.

### Caching
Redis is the cache store with a 30-minute default TTL. Use `@Cacheable` / `@CacheEvict` annotations, or inject `CacheService` for programmatic access. Cache configuration lives in `common-lib`.

## Infrastructure

### Docker
Each runnable service has a `Dockerfile` using `eclipse-temurin:21-jdk-alpine` (auth-service) or `openjdk:21-jdk` (third-party-service). The built JAR is named `{artifactId}-{version}-{profile}.jar`.

### Kubernetes
**All K8s deployment manifests live in the dedicated GitOps repository [`flyingjack-cloud/k8s-gitops`](https://github.com/flyingjack-cloud/k8s-gitops) — do not add k8s config to service repos.**

Structure in that repo: `{service}/base/` and `{service}/overlays/{profile}/` (Kustomize). Namespace pattern: `flyingjack-{profile}`. For first-time deployment, manual Secret setup, and ArgoCD Application creation, see each service's `DEPLOY.md`.

## CI/CD Pipeline

### Overview
CI/CD uses **GitHub Actions (self-hosted runner)** for build/push and **ArgoCD** for GitOps-based deployment. Images are stored in a **self-hosted container registry**.

### Pipeline Flow
```
git push → GitHub Actions (self-hosted) → build & test → docker build & push → update K8s manifests → ArgoCD sync → Kubernetes
```

### GitHub Actions (CI)
- Runs on a **self-hosted runner** (not GitHub-hosted).
- Per-service workflows triggered by pushes to submodule repos or the root repo.
- Steps: Maven build → Docker build → push to self-hosted registry → update image tag in K8s manifests.
- Image tag convention: `{registry}/{service}:{profile}-{git-sha}` (e.g., `registry.flyingjack.com/auth-service:dev-abc1234`).

### Container Registry
- Self-hosted registry (not Docker Hub or a cloud registry).
- Registry address should be referenced from environment/secrets — never hardcoded in Dockerfiles or manifests.

### ArgoCD (CD)
- ArgoCD watches `flyingjack-cloud/k8s-gitops` and syncs to the target cluster automatically.
- Each profile (`beta`, `prod`) maps to an ArgoCD Application pointing at `{service}/overlays/{profile}/`.
- ArgoCD is the **sole mechanism** for applying K8s changes — never `kubectl apply` directly in non-dev clusters.

## Testing

Integration tests use **TestContainers** for PostgreSQL and Redis. Extend `BaseContainerTest` to get pre-configured containers. Tests are in `application-test` profile.
