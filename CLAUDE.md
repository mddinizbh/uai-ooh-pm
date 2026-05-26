# uai-buslines — CLAUDE.md

Service: **BH Bus Lines — Neighborhood Bus Explorer**  
Package: `com.uai.buslines` · Port: `8085` · Stack: Java 21 / Spring Boot 3.3.6 / Maven

## Architecture

Hexagonal (ports & adapters), single Maven module.

```
com.uai.buslines
├── domain/
│   ├── model/          ← pure domain types, no framework deps
│   ├── port/in/        ← driving ports (use case interfaces)
│   └── port/out/       ← driven ports (repository / gateway interfaces)
├── application/
│   └── usecase/        ← use case implementations
└── adapter/
    ├── in/web/         ← REST controllers + DTOs
    └── out/persistence/← JPA entities + repositories
```

## Key Architectural Deviations

### ⚠️ No `tenant_id` column (ADR-004)

**This is intentional.** uAI RULE-JAVA-03 mandates `tenant_id UUID NOT NULL` on all tables.
BH Bus Lines is a **public, single-tenant civic tool** — no authentication, no per-user data.
Adding `tenant_id` would be YAGNI for a product with no tenancy model.

References: [ADR-004](../.compozy/tasks/bh-bus-lines/adrs/adr-004.md)

### ⚠️ No PostGIS — geometry stored as `jsonb` GeoJSON (ADR-003)

The shared uAI database is `postgres:16-alpine` (no PostGIS extension).
Neighbourhood classification (PASSES_THROUGH / DEPARTS_FROM / ARRIVES_AT) is precomputed
at GTFS import time using **JTS** (`org.locationtech.jts`) and stored in `line_neighborhood`.
Runtime queries are plain indexed SQL joins — no spatial engine at query time.

References: [ADR-003](../.compozy/tasks/bh-bus-lines/adrs/adr-003.md)

## Rules

- Sealed classes/interfaces MUST NOT have `default` in switch expressions (uAI RULE-JAVA-02).
- `LineRelation` is the canonical sealed type for line↔neighborhood classification.
- JPA entities (task_04) MUST match `V1__create_tables.sql` exactly — schema is the source of truth.
- Never add PostGIS types or `tenant_id` to any table without an ADR update.
- `hibernate.ddl-auto=validate` — schema is owned by Flyway, not Hibernate.

## Configuration

| Env var         | Description                    | Docker default |
|-----------------|-------------------------------|----------------|
| `DB_HOST`       | Postgres hostname              | `postgres`     |
| `DB_PORT`       | Postgres port                  | `5432`         |
| `DB_NAME`       | Database name                  | `uai_buslines` |
| `DB_USER`       | Postgres user                  | `${PG_USER}`   |
| `DB_PASSWORD`   | Postgres password              | `${PG_PASSWORD}` |
| `UAI_INTERNAL_API_KEY` | Protects `POST /api/internal/import` | required |

## OpenAPI

Available at `/api/docs` (Swagger UI at `/api/swagger-ui.html`) — populated in task_06.

## Testing

- Integration tests use Testcontainers `postgres:16` — plain Postgres, no PostGIS needed.
- Test profile: `@ActiveProfiles("test")` — `src/test/resources/application-test.yml`.
- Coverage target: ≥80% lines (JaCoCo check in `mvn verify`).
- Tests named `*IT.java` run under **Failsafe** (integration-test phase); `*Test.java`/`*Tests.java` run under Surefire.

## Developer Notes

### Running tests on macOS with Docker Desktop 29.x

Docker Desktop 29.x sets `MinAPIVersion=1.44`. The shaded docker-java bundled with
Testcontainers defaults to API 1.41, which Docker 29.x rejects with HTTP 400.

**Fix already applied in `pom.xml`**: both `maven-surefire-plugin` and `maven-failsafe-plugin`
set `<api.version>1.44</api.version>` via `systemPropertyVariables`. Do not remove this.

Testcontainers is pinned to `1.21.3` (overrides Spring Boot BOM's 1.19.8) for the same reason.

### Java version

Always run Maven with Java 21. If Homebrew resolves to Java 25, JaCoCo 0.8.12 fails
with "Unsupported class file major version 69". Use:

```
JAVA_HOME=/path/to/java-21 mvn verify
```
