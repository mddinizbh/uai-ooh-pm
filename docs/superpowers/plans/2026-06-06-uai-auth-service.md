# uai-auth Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (- [ ]) syntax.

**Goal:** Build the `uai-auth` service from an empty repo — a stateless Spring Boot 3 authentication service issuing RS256 JWTs, with login/refresh/logout/me, an internal introspection + revoke API, a JWKS endpoint, Redis jti-blacklist revocation, Postgres+Flyway persistence, BCrypt(12) hashing, full unit + Testcontainers integration tests, a Dockerfile and a GHCR CI workflow.

**Architecture:** Hexagonal (ports & adapters), base package `com.uai.auth`, single Maven module — `domain/{model,port}`, `application/usecase`, `adapter/{in/web, in/web/security, out/persistence, out/redis, out/token}`. Spring Security is stateless: a `JwtAuthenticationFilter` verifies the service's own access JWT (RS256) locally for Bearer endpoints, an `ApiKeyAuthFilter` guards the internal `introspect`/`revoke` endpoints with a timing-safe `X-UAI-Internal-Key` check; revocation is immediate via a Redis `blacklist:jti:{jti}` key. The service owns the `uai_auth` Postgres schema through Flyway migrations applied on startup (ADR-047).

**Tech Stack:** Java 21 · Spring Boot 3.3.6 (web, security, data-jpa, data-redis, validation, actuator) · Flyway 10 (+ flyway-database-postgresql) · PostgreSQL · Redis · jjwt 0.12.6 (RS256) · BCrypt strength 12 · JUnit 5 + Mockito + Testcontainers 1.21.3 (Postgres + Redis) · JaCoCo ≥80% · Maven (no wrapper) · multi-stage Docker · GitHub Actions → GHCR.

---

## File Structure

> All paths are relative to `/Users/marleydiniz/IdeaProjects/personal/uai/uai-auth`. Base package dir = `src/main/java/com/uai/auth/`, tests = `src/test/java/com/uai/auth/`.

### Build / ops
- `pom.xml` — Spring Boot 3.3.6 parent, Java 21, all deps + Surefire/Failsafe/JaCoCo plugins.
- `.gitignore` — target/, .env, *.log, /.idea/.
- `.dockerignore` — keep image context small (target, .git, .idea).
- `Dockerfile` — multi-stage maven:3.9-eclipse-temurin-21 → eclipse-temurin:21-jre-alpine, EXPOSE 8084.
- `.github/workflows/deploy.yml` — `CI`: test (mvn verify) → build-push to GHCR.
- `README.md` — endpoints, run, openssl keypair generation, env vars.

### Resources
- `src/main/resources/application.yml` — base config (port 8084, datasource, jpa validate, flyway, redis, jackson snake_case, actuator, `uai.auth.*`).
- `src/main/resources/application-prod.yml` — prod datasource (no default creds), logging.
- `src/main/resources/db/migration/V1__init.sql` — tenant / user_account / refresh_token schema + indexes.
- `src/main/resources/db/migration/V2__seed_tenant_and_admins.sql` — tenant "uAI" + 2 ADMIN users (hash placeholders).
- `src/test/resources/application-test.yml` — test profile (internal api-key, validate, flyway on).

### Domain
- `domain/model/Role.java` — enum: ADMIN, AGENCY_MANAGER, CLIENT_VIEWER, SELF_SERVICE_USER (+ `authority()`).
- `domain/model/User.java` — record (id, tenantId, email, passwordHash, role, enabled, createdAt, lastLoginAt).
- `domain/model/Tenant.java` — record (id, name, active, createdAt).
- `domain/model/RefreshToken.java` — record (id, userId, expiresAt, revokedAt, createdAt).
- `domain/model/TokenPair.java` — record (accessToken, refreshToken, expiresInSeconds).
- `domain/model/AccessClaims.java` — record (userId, tenantId, role, email, jti, expiresAt).
- `domain/model/IssuedAccessToken.java` — record (token, jti, expiresAt).
- `domain/model/IssuedRefreshToken.java` — record (rawToken, expiresAt).
- `domain/model/IntrospectionResult.java` — record (active, userId, email, tenantId, role, expiresAt) + factories.
- `domain/model/TokenInvalidException.java` — RuntimeException (verifier).
- `domain/model/InvalidCredentialsException.java` — RuntimeException (login → 401).
- `domain/model/InvalidRefreshTokenException.java` — RuntimeException (refresh → 401).
- `domain/port/in/LoginUseCase.java`, `RefreshUseCase.java`, `LogoutUseCase.java`, `IntrospectUseCase.java`, `RevokeUseCase.java` — driving ports.
- `domain/port/out/UserRepository.java`, `TenantRepository.java`, `RefreshTokenRepository.java`, `TokenBlacklist.java`, `TokenIssuer.java`, `TokenVerifier.java` — driven ports.

### Application
- `application/usecase/LoginService.java`, `RefreshService.java`, `LogoutService.java`, `IntrospectService.java`, `RevokeService.java`.

### Adapters
- `adapter/out/token/JwtProperties.java` — `@ConfigurationProperties("uai.auth.jwt")`.
- `adapter/out/token/RsaKeyProvider.java` — parse PEM or generate ephemeral keypair.
- `adapter/out/token/JwtTokenIssuer.java` — implements `TokenIssuer` (jjwt RS256).
- `adapter/out/token/JwtTokenVerifier.java` — implements `TokenVerifier`.
- `adapter/out/persistence/TenantEntity.java`, `UserAccountEntity.java`, `RefreshTokenEntity.java` — JPA entities.
- `adapter/out/persistence/TenantJpaRepository.java`, `UserAccountJpaRepository.java`, `RefreshTokenJpaRepository.java` — Spring Data.
- `adapter/out/persistence/TenantRepositoryAdapter.java`, `UserRepositoryAdapter.java`, `RefreshTokenRepositoryAdapter.java` — port impls (+ SHA-256 hashing).
- `adapter/out/redis/RedisTokenBlacklist.java` — implements `TokenBlacklist`.
- `adapter/in/web/dto/LoginRequest.java`, `RefreshRequest.java`, `IntrospectRequest.java`, `RevokeRequest.java`, `TokenResponse.java`, `MeResponse.java`, `IntrospectResponse.java`.
- `adapter/in/web/AuthController.java`, `IntrospectionController.java`, `RevokeController.java`, `JwksController.java`, `ApiExceptionHandler.java`.
- `adapter/in/web/security/AuthPrincipal.java`, `JwtAuthenticationFilter.java`, `ApiKeyAuthFilter.java`, `SecurityConfig.java`.

### Config / entrypoint
- `AuthApplication.java` — `@SpringBootApplication` + `@EnableConfigurationProperties(JwtProperties.class)`.
- `config/AppConfig.java` — `PasswordEncoder` (BCrypt 12) + `Clock` beans.

### Tests
- `domain/model/RoleTest.java`
- `config/AppConfigTest.java`
- `adapter/out/token/RsaKeyProviderTest.java`, `JwtTokenIssuerTest.java`, `JwtTokenVerifierTest.java`
- `application/usecase/LoginServiceTest.java`, `RefreshServiceTest.java`, `LogoutServiceTest.java`, `RevokeServiceTest.java`, `IntrospectServiceTest.java`
- `adapter/out/persistence/PersistenceAdaptersIT.java`
- `adapter/out/redis/RedisTokenBlacklistIT.java`
- `adapter/in/web/AuthControllerTest.java`
- `adapter/in/web/AuthFlowIT.java` — full Testcontainers PG+Redis flow.
- `tools/BcryptHashGeneratorTest.java` — gated hash generator (USER runs it).

---

### Task 1: Project scaffold + Role enum

**Files:** Create `pom.xml`, `.gitignore`, `.dockerignore`, `AuthApplication.java`, `src/main/resources/application.yml`, `src/main/resources/application-prod.yml`, `domain/model/Role.java`.
**Test:** `src/test/java/com/uai/auth/domain/model/RoleTest.java`

- [ ] Step: Write the failing test.
```java
// src/test/java/com/uai/auth/domain/model/RoleTest.java
package com.uai.auth.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoleTest {

    @Test
    void hasTheFourContractRoles() {
        assertThat(Role.values()).containsExactly(
                Role.ADMIN, Role.AGENCY_MANAGER, Role.CLIENT_VIEWER, Role.SELF_SERVICE_USER);
    }

    @Test
    void authorityIsRolePrefixed() {
        assertThat(Role.ADMIN.authority()).isEqualTo("ROLE_ADMIN");
        assertThat(Role.CLIENT_VIEWER.authority()).isEqualTo("ROLE_CLIENT_VIEWER");
    }

    @Test
    void valueOfRejectsUnknown() {
        assertThatThrownBy(() -> Role.valueOf("ROOT")).isInstanceOf(IllegalArgumentException.class);
    }
}
```
- [ ] Step: Run it, verify it FAILS — `mvn -q -Dtest=RoleTest test` fails to compile (`pom.xml`/`Role` do not exist yet → "The goal you specified requires a project to perform but there is no POM" or compilation failure).
- [ ] Step: Create `pom.xml`.
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.6</version>
        <relativePath/>
    </parent>

    <groupId>com.uai</groupId>
    <artifactId>uai-auth</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>uai-auth</name>
    <description>uAI dedicated authentication service — JWT RS256, BCrypt(12), Redis blacklist (Java 21)</description>
    <packaging>jar</packaging>

    <properties>
        <java.version>21</java.version>
        <jjwt.version>0.12.6</jjwt.version>
        <jacoco.version>0.8.12</jacoco.version>
        <!-- Docker Desktop 29.x requires MinAPIVersion=1.44; 1.21.3 is known-good. -->
        <testcontainers.version>1.21.3</testcontainers.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Flyway 10 needs the Postgres module split-out (ADR-047 — migrate on startup). -->
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- jjwt RS256 (not managed by the Boot BOM) -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jjwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jjwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- ── Test ── -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>
                    <systemPropertyVariables>
                        <api.version>1.44</api.version>
                    </systemPropertyVariables>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
                <configuration>
                    <systemPropertyVariables>
                        <api.version>1.44</api.version>
                    </systemPropertyVariables>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>integration-test</goal>
                            <goal>verify</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>${jacoco.version}</version>
                <executions>
                    <execution>
                        <id>prepare-agent</id>
                        <goals><goal>prepare-agent</goal></goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>verify</phase>
                        <goals><goal>report</goal></goals>
                    </execution>
                    <execution>
                        <id>check</id>
                        <phase>verify</phase>
                        <goals><goal>check</goal></goals>
                        <configuration>
                            <excludes>
                                <exclude>com/uai/auth/AuthApplication.class</exclude>
                            </excludes>
                            <rules>
                                <rule>
                                    <element>BUNDLE</element>
                                    <limits>
                                        <limit>
                                            <counter>LINE</counter>
                                            <value>COVEREDRATIO</value>
                                            <minimum>0.80</minimum>
                                        </limit>
                                    </limits>
                                </rule>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```
- [ ] Step: Create `.gitignore`, `.dockerignore`.
```gitignore
# .gitignore
/target/
target/
.env
*.log
/.idea/
.idea/
```
```gitignore
# .dockerignore
target
.git
.github
.idea
*.md
```
- [ ] Step: Create `AuthApplication.java` and `domain/model/Role.java`.
```java
// src/main/java/com/uai/auth/AuthApplication.java
package com.uai.auth;

import com.uai.auth.adapter.out.token.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * uAI dedicated authentication service (:8084).
 *
 * <p>Hexagonal (ports &amp; adapters), single Maven module, base package {@code com.uai.auth}.
 * Issues RS256 JWTs, validates its own Bearer tokens locally, exposes an internal
 * introspection/revoke API ({@code X-UAI-Internal-Key}) and a JWKS endpoint. Flyway owns the
 * {@code uai_auth} schema and migrates on startup (ADR-047).
 */
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
```
```java
// src/main/java/com/uai/auth/domain/model/Role.java
package com.uai.auth.domain.model;

/**
 * uAI authorization roles. Pure labels (no per-variant data) → modelled as an {@code enum}
 * (RULE-JAVA-02 reserves sealed interfaces for variants that carry data).
 */
public enum Role {
    ADMIN,
    AGENCY_MANAGER,
    CLIENT_VIEWER,
    SELF_SERVICE_USER;

    /** Spring Security authority name, e.g. {@code ROLE_ADMIN}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
```
> Note: `AuthApplication` references `JwtProperties` (Task 3). To keep Task 1's `mvn -Dtest=RoleTest test` self-contained, temporarily omit the `@EnableConfigurationProperties` import+annotation now and add it in Task 3, OR create the `JwtProperties` stub here. Simplest: in Task 1 write `AuthApplication` WITHOUT the `JwtProperties` import/annotation (plain `@SpringBootApplication`); Task 3 adds them back. Use the plain version below for Task 1:
```java
// src/main/java/com/uai/auth/AuthApplication.java  (Task 1 version — annotation added in Task 3)
package com.uai.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
```
- [ ] Step: Create `application.yml` and `application-prod.yml`.
```yaml
# src/main/resources/application.yml
server:
  port: ${SERVER_PORT:8084}

spring:
  application:
    name: uai-auth
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:uai_auth}
    username: ${DB_USER:uai}
    password: ${DB_PASSWORD:uai}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  jackson:
    property-naming-strategy: SNAKE_CASE

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: always

uai:
  auth:
    jwt:
      issuer: uai-auth
      kid: uai-auth
      access-ttl: PT15M
      refresh-ttl: P7D
      # RSA keypair (PEM). Blank ⇒ ephemeral keypair generated at boot (DEV/TEST ONLY).
      private-key: ${UAI_AUTH_JWT_PRIVATE_KEY:}
      public-key: ${UAI_AUTH_JWT_PUBLIC_KEY:}
    internal:
      # Reuses the existing uai-infra secret. Blank ⇒ internal endpoints are rejected (fail-safe).
      api-key: ${UAI_INTERNAL_API_KEY:}
```
```yaml
# src/main/resources/application-prod.yml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT:5432}/${DB_NAME:uai_auth}
    username: ${DB_USER}
    password: ${DB_PASSWORD}

logging:
  level:
    com.uai.auth: INFO
```
- [ ] Step: Run tests, verify PASS — `mvn -q -Dtest=RoleTest test` → `BUILD SUCCESS`, 3 tests pass.
- [ ] Step: Commit — `git add -A && git commit -m "chore(auth): scaffold Spring Boot 3 project + Role enum"`

---

### Task 2: BCrypt(12) password encoder + Clock bean

**Files:** Create `config/AppConfig.java`.
**Test:** `src/test/java/com/uai/auth/config/AppConfigTest.java`

- [ ] Step: Write the failing test.
```java
// src/test/java/com/uai/auth/config/AppConfigTest.java
package com.uai.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTest {

    private final PasswordEncoder encoder = new AppConfig().passwordEncoder();

    @Test
    void encodesWithBcryptStrength12() {
        String hash = encoder.encode("hunter2");
        // BCrypt strength 12 → "$2a$12$" (Spring's BCryptPasswordEncoder emits $2a$).
        assertThat(hash).startsWith("$2a$12$");
    }

    @Test
    void matchesTheRawPassword() {
        String hash = encoder.encode("hunter2");
        assertThat(encoder.matches("hunter2", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }
}
```
- [ ] Step: Run it, verify it FAILS — `mvn -q -Dtest=AppConfigTest test` → compile error (`AppConfig` missing).
- [ ] Step: Minimal implementation.
```java
// src/main/java/com/uai/auth/config/AppConfig.java
package com.uai.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;

/** Cross-cutting beans: the BCrypt(12) password encoder and a UTC {@link Clock} (testable time). */
@Configuration
public class AppConfig {

    public static final int BCRYPT_STRENGTH = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```
- [ ] Step: Run tests, verify PASS — `mvn -q -Dtest=AppConfigTest test` → 2 tests pass.
- [ ] Step: Commit — `git add -A && git commit -m "feat(auth): BCrypt(12) password encoder + Clock bean"`

---

### Task 3: RSA key provider (env PEM or ephemeral) + JwtProperties

**Files:** Create `adapter/out/token/JwtProperties.java`, `adapter/out/token/RsaKeyProvider.java`. Modify `AuthApplication.java` (re-add `@EnableConfigurationProperties(JwtProperties.class)`).
**Test:** `src/test/java/com/uai/auth/adapter/out/token/RsaKeyProviderTest.java`

- [ ] Step: Write the failing test.
```java
// src/test/java/com/uai/auth/adapter/out/token/RsaKeyProviderTest.java
package com.uai.auth.adapter.out.token;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class RsaKeyProviderTest {

    private static JwtProperties props(String priv, String pub) {
        return new JwtProperties("uai-auth", "uai-auth",
                Duration.ofMinutes(15), Duration.ofDays(7), priv, pub);
    }

    @Test
    void generatesEphemeralKeypairWhenPemsBlank() {
        RsaKeyProvider provider = new RsaKeyProvider(props("", ""));
        assertThat(provider.privateKey()).isNotNull();
        assertThat(provider.publicKey().getModulus().bitLength()).isBetween(2040, 2048);
    }

    @Test
    void parsesPemKeypairFromConfig() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        String privPem = pem("PRIVATE KEY", kp.getPrivate().getEncoded());   // PKCS8
        String pubPem = pem("PUBLIC KEY", kp.getPublic().getEncoded());      // X.509

        RsaKeyProvider provider = new RsaKeyProvider(props(privPem, pubPem));

        assertThat(provider.publicKey().getModulus())
                .isEqualTo(((RSAPublicKey) kp.getPublic()).getModulus());
    }

    private static String pem(String type, byte[] der) {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
    }
}
```
- [ ] Step: Run it, verify it FAILS — `mvn -q -Dtest=RsaKeyProviderTest test` → compile error (`JwtProperties`, `RsaKeyProvider` missing).
- [ ] Step: Implement `JwtProperties` and `RsaKeyProvider`.
```java
// src/main/java/com/uai/auth/adapter/out/token/JwtProperties.java
package com.uai.auth.adapter.out.token;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Bound from {@code uai.auth.jwt.*}.
 *
 * @param issuer     JWT {@code iss} claim
 * @param kid        key id advertised in JWKS and the access token header
 * @param accessTtl  access-token lifetime (default 15min)
 * @param refreshTtl refresh-token lifetime (default 7d)
 * @param privateKey RSA private key PEM (PKCS8); blank ⇒ ephemeral keypair
 * @param publicKey  RSA public key PEM (X.509); blank ⇒ ephemeral keypair
 */
@ConfigurationProperties("uai.auth.jwt")
public record JwtProperties(
        String issuer,
        String kid,
        Duration accessTtl,
        Duration refreshTtl,
        String privateKey,
        String publicKey) {

    public JwtProperties {
        if (issuer == null || issuer.isBlank()) issuer = "uai-auth";
        if (kid == null || kid.isBlank()) kid = "uai-auth";
        if (accessTtl == null) accessTtl = Duration.ofMinutes(15);
        if (refreshTtl == null) refreshTtl = Duration.ofDays(7);
        if (privateKey == null) privateKey = "";
        if (publicKey == null) publicKey = "";
    }
}
```
```java
// src/main/java/com/uai/auth/adapter/out/token/RsaKeyProvider.java
package com.uai.auth.adapter.out.token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Supplies the RS256 keypair. If both PEMs are configured they are parsed; otherwise an
 * ephemeral 2048-bit keypair is generated at boot (DEV/TEST only — emits a WARN).
 */
@Component
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public RsaKeyProvider(JwtProperties props) {
        if (!props.privateKey().isBlank() && !props.publicKey().isBlank()) {
            this.privateKey = parsePrivate(props.privateKey());
            this.publicKey = parsePublic(props.publicKey());
            log.info("Loaded RS256 keypair from configuration (kid={}).", props.kid());
        } else {
            log.warn("No RSA keypair configured (uai.auth.jwt.private-key/public-key blank) — "
                    + "generating an EPHEMERAL keypair. DEV/TEST ONLY; set the env vars in production.");
            KeyPair kp = generate();
            this.privateKey = (RSAPrivateKey) kp.getPrivate();
            this.publicKey = (RSAPublicKey) kp.getPublic();
        }
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    static RSAPrivateKey parsePrivate(String pem) {
        try {
            byte[] der = body(pem, "PRIVATE KEY");
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA private key PEM", e);
        }
    }

    static RSAPublicKey parsePublic(String pem) {
        try {
            byte[] der = body(pem, "PUBLIC KEY");
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid RSA public key PEM", e);
        }
    }

    private static byte[] body(String pem, String type) {
        String b64 = pem
                .replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(b64);
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate ephemeral RSA keypair", e);
        }
    }
}
```
- [ ] Step: Re-add the config-properties annotation on `AuthApplication`.
```java
// src/main/java/com/uai/auth/AuthApplication.java
package com.uai.auth;

import com.uai.auth.adapter.out.token.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}
```
- [ ] Step: Run tests, verify PASS — `mvn -q -Dtest=RsaKeyProviderTest test` → 2 tests pass.
- [ ] Step: Commit — `git add -A && git commit -m "feat(auth): RSA key provider (env PEM or ephemeral) + JwtProperties"`

---

### Task 4: JWT issuer + verifier (jjwt RS256) + token value records + ports

**Files:** Create `domain/model/User.java`, `domain/model/AccessClaims.java`, `domain/model/IssuedAccessToken.java`, `domain/model/IssuedRefreshToken.java`, `domain/model/TokenInvalidException.java`, `domain/port/out/TokenIssuer.java`, `domain/port/out/TokenVerifier.java`, `adapter/out/token/JwtTokenIssuer.java`, `adapter/out/token/JwtTokenVerifier.java`.
**Test:** `src/test/java/com/uai/auth/adapter/out/token/JwtTokenIssuerTest.java`, `JwtTokenVerifierTest.java`

- [ ] Step: Write the failing issuer test.
```java
// src/test/java/com/uai/auth/adapter/out/token/JwtTokenIssuerTest.java
package com.uai.auth.adapter.out.token;

import com.uai.auth.domain.model.IssuedAccessToken;
import com.uai.auth.domain.model.IssuedRefreshToken;
import com.uai.auth.domain.model.Role;
import com.uai.auth.domain.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenIssuerTest {

    private final Instant now = Instant.parse("2026-06-06T12:00:00Z");
    private final RsaKeyProvider keys = new RsaKeyProvider(
            new JwtProperties("uai-auth", "uai-auth", Duration.ofMinutes(15), Duration.ofDays(7), "", ""));
    private final JwtProperties props =
            new JwtProperties("uai-auth", "uai-auth", Duration.ofMinutes(15), Duration.ofDays(7), "", "");
    private final JwtTokenIssuer issuer =
            new JwtTokenIssuer(keys, props, Clock.fixed(now, ZoneOffset.UTC));

    private static User user() {
        return new User(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                "v@uai.test", "$2a$12$hash", Role.ADMIN, true, Instant.EPOCH, null);
    }

    @Test
    void accessTokenCarriesAllContractClaims() {
        IssuedAccessToken issued = issuer.issueAccessToken(user());

        Claims c = Jwts.parser().verifyWith(keys.publicKey()).build()
                .parseSignedClaims(issued.token()).getPayload();

        assertThat(c.getSubject()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(c.get("tenant_id", String.class)).isEqualTo("22222222-2222-2222-2222-222222222222");
        assertThat(c.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(c.get("email", String.class)).isEqualTo("v@uai.test");
        assertThat(c.getId()).isEqualTo(issued.jti()).isNotBlank();
        assertThat(c.getIssuer()).isEqualTo("uai-auth");
        assertThat(c.getExpiration().toInstant()).isEqualTo(now.plus(Duration.ofMinutes(15)));
        assertThat(issued.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
    }

    @Test
    void eachAccessTokenHasAUniqueJti() {
        assertThat(issuer.issueAccessToken(user()).jti())
                .isNotEqualTo(issuer.issueAccessToken(user()).jti());
    }

    @Test
    void refreshTokenIsOpaqueRandomWithSevenDayExpiry() {
        IssuedRefreshToken rt = issuer.issueRefreshToken();
        assertThat(rt.rawToken()).hasSizeGreaterThanOrEqualTo(40);
        assertThat(rt.expiresAt()).isEqualTo(now.plus(Duration.ofDays(7)));
        assertThat(issuer.issueRefreshToken().rawToken()).isNotEqualTo(rt.rawToken());
    }
}
```
- [ ] Step: Run it, verify it FAILS — `mvn -q -Dtest=JwtTokenIssuerTest test` → compile error (types missing).
- [ ] Step: Implement domain records + ports.
```java
// src/main/java/com/uai/auth/domain/model/User.java
package com.uai.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id, UUID tenantId, String email, String passwordHash,
        Role role, boolean enabled, Instant createdAt, Instant lastLoginAt) {
}
```
```java
// src/main/java/com/uai/auth/domain/model/AccessClaims.java
package com.uai.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

/** Verified claims extracted from an access JWT. */
public record AccessClaims(UUID userId, UUID tenantId, Role role, String email, String jti, Instant expiresAt) {
}
```
```java
// src/main/java/com/uai/auth/domain/model/IssuedAccessToken.java
package com.uai.auth.domain.model;

import java.time.Instant;

public record IssuedAccessToken(String token, String jti, Instant expiresAt) {
}
```
```java
// src/main/java/com/uai/auth/domain/model/IssuedRefreshToken.java
package com.uai.auth.domain.model;

import java.time.Instant;

public record IssuedRefreshToken(String rawToken, Instant expiresAt) {
}
```
```java
// src/main/java/com/uai/auth/domain/model/TokenInvalidException.java
package com.uai.auth.domain.model;

/** Raised by {@code TokenVerifier} when a token is unparseable, badly signed, or expired. */
public class TokenInvalidException extends RuntimeException {
    public TokenInvalidException(String message, Throwable cause) {
        super(message, cause);
    }
}
```
```java
// src/main/java/com/uai/auth/domain/port/out/TokenIssuer.java
package com.uai.auth.domain.port.out;

import com.uai.auth.domain.model.IssuedAccessToken;
import com.uai.auth.domain.model.IssuedRefreshToken;
import com.uai.auth.domain.model.User;

public interface TokenIssuer {
    IssuedAccessToken issueAccessToken(User user);

    IssuedRefreshToken issueRefreshToken();
}
```
```java
// src/main/java/com/uai/auth/domain/port/out/TokenVerifier.java
package com.uai.auth.domain.port.out;

import com.uai.auth.domain.model.AccessClaims;
import com.uai.auth.domain.model.TokenInvalidException;

public interface TokenVerifier {
    AccessClaims verify(String token) throws TokenInvalidException;
}
```
- [ ] Step: Implement `JwtTokenIssuer`.
```java
// src/main/java/com/uai/auth/adapter/out/token/JwtTokenIssuer.java
package com.uai.auth.adapter.out.token;

import com.uai.auth.domain.model.IssuedAccessToken;
import com.uai.auth.domain.model.IssuedRefreshToken;
import com.uai.auth.domain.model.User;
import com.uai.auth.domain.port.out.TokenIssuer;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenIssuer implements TokenIssuer {

    private final RsaKeyProvider keys;
    private final JwtProperties props;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public JwtTokenIssuer(RsaKeyProvider keys, JwtProperties props, Clock clock) {
        this.keys = keys;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public IssuedAccessToken issueAccessToken(User user) {
        Instant now = clock.instant();
        Instant exp = now.plus(props.accessTtl());
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .header().keyId(props.kid()).and()
                .subject(user.id().toString())
                .issuer(props.issuer())
                .claim("tenant_id", user.tenantId().toString())
                .claim("role", user.role().name())
                .claim("email", user.email())
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(keys.privateKey(), Jwts.SIG.RS256)
                .compact();
        return new IssuedAccessToken(token, jti, exp);
    }

    @Override
    public IssuedRefreshToken issueRefreshToken() {
        byte[] buf = new byte[32];
        random.nextBytes(buf);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        return new IssuedRefreshToken(raw, clock.instant().plus(props.refreshTtl()));
    }
}
```
- [ ] Step: Run tests, verify PASS — `mvn -q -Dtest=JwtTokenIssuerTest test` → 3 tests pass.
- [ ] Step: Write the failing verifier test.
```java
// src/test/java/com/uai/auth/adapter/out/token/JwtTokenVerifierTest.java
package com.uai.auth.adapter.out.token;

import com.uai.auth.domain.model.AccessClaims;
import com.uai.auth.domain.model.Role;
import com.uai.auth.domain.model.TokenInvalidException;
import com.uai.auth.domain.model.User;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenVerifierTest {

    private final RsaKeyProvider keys = new RsaKeyProvider(
            new JwtProperties("uai-auth", "uai-auth", Duration.ofMinutes(15), Duration.ofDays(7), "", ""));
    private final JwtProperties props =
            new JwtProperties("uai-auth", "uai-auth", Duration.ofMinutes(15), Duration.ofDays(7), "", "");
    private final JwtTokenVerifier verifier = new JwtTokenVerifier(keys);

    private User user() {
        return new User(UUID.randomUUID(), UUID.randomUUID(), "x@uai.test",
                "$2a$12$h", Role.AGENCY_MANAGER, true, Instant.EPOCH, null);
    }

    @Test
    void verifiesAValidTokenAndExtractsClaims() {
        User u = user();
        var issuer = new JwtTokenIssuer(keys, props, Clock.systemUTC());
        String token = issuer.issueAccessToken(u).token();

        AccessClaims c = verifier.verify(token);

        assertThat(c.userId()).isEqualTo(u.id());
        assertThat(c.tenantId()).isEqualTo(u.tenantId());
        assertThat(c.role()).isEqualTo(Role.AGENCY_MANAGER);
        assertThat(c.email()).isEqualTo("x@uai.test");
        assertThat(c.jti()).isNotBlank();
    }

    @Test
    void rejectsATamperedToken() {
        var issuer = new JwtTokenIssuer(keys, props, Clock.systemUTC());
        String token = issuer.issueAccessToken(user()).token();
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> verifier.verify(tampered)).isInstanceOf(TokenInvalidException.class);
    }

    @Test
    void rejectsAnExpiredToken() {
        Instant past = Instant.parse("2020-01-01T00:00:00Z");
        var issuer = new JwtTokenIssuer(keys, props, Clock.fixed(past, ZoneOffset.UTC));
        String expired = issuer.issueAccessToken(user()).token();

        assertThatThrownBy(() -> verifier.verify(expired)).isInstanceOf(TokenInvalidException.class);
    }
}
```
- [ ] Step: Run it, verify it FAILS — `mvn -q -Dtest=JwtTokenVerifierTest test` → compile error (`JwtTokenVerifier` missing).
- [ ] Step: Implement `JwtTokenVerifier`.
```java
// src/main/java/com/uai/auth/adapter/out/token/JwtTokenVerifier.java
package com.uai.auth.adapter.out.token;

import com.uai.auth.domain.model.AccessClaims;
import com.uai.auth.domain.model.Role;
import com.uai.auth.domain.model.TokenInvalidException;
import com.uai.auth.domain.port.out.TokenVerifier;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class JwtTokenVerifier implements TokenVerifier {

    private final RsaKeyProvider keys;

    public JwtTokenVerifier(RsaKeyProvider keys) {
        this.keys = keys;
    }

    @Override
    public AccessClaims verify(String token) {
        try {
            Claims c = Jwts.parser()
                    .verifyWith(keys.publicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AccessClaims(
                    UUID.fromString(c.getSubject()),
                    UUID.fromString(c.get("tenant_id", String.class)),
                    Role.valueOf(c.get("role", String.class)),
                    c.get("email", String.class),
                    c.getId(),
                    c.getExpiration().toInstant());
        } catch (JwtException | IllegalArgumentException e) {
            throw new TokenInvalidException("invalid token", e);
        }
    }
}
```
- [ ] Step: Run tests, verify PASS — `mvn -q -Dtest=JwtTokenVerifierTest test` → 3 tests pass.
- [ ] Step: Commit — `git add -A && git commit -m "feat(auth): RS256 JWT issuer + verifier (jjwt) with claims/jti/exp"`

---

### Task 5: Flyway V1 schema + JPA entities + persistence adapters

**Files:** Create `src/main/resources/db/migration/V1__init.sql`; `domain/model/Tenant.java`, `domain/model/RefreshToken.java`; `domain/port/out/UserRepository.java`, `TenantRepository.java`, `RefreshTokenRepository.java`; `adapter/out/persistence/{TenantEntity,UserAccountEntity,RefreshTokenEntity,TenantJpaRepository,UserAccountJpaRepository,RefreshTokenJpaRepository,TenantRepositoryAdapter,UserRepositoryAdapter,RefreshTokenRepositoryAdapter}.java`; `src/test/resources/application-test.yml`. Also create `src/main/resources/db/migration/V2__seed_tenant_and_admins.sql` minimal seed now (needed so the IT has a user+tenant to read).
**Test:** `src/test/java/com/uai/auth/adapter/out/persistence/PersistenceAdaptersIT.java`

- [ ] Step: Write the failing integration test.
```java
// src/test/java/com/uai/auth/adapter/out/persistence/PersistenceAdaptersIT.java
package com.uai.auth.adapter.out.persistence;

import com.uai.auth.domain.model.RefreshToken;
import com.uai.auth.domain.model.Role;
import com.uai.auth.domain.model.Tenant;
import com.uai.auth.domain.model.User;
import com.uai.auth.domain.port.out.RefreshTokenRepository;
import com.uai.auth.domain.port.out.TenantRepository;
import com.uai.auth.domain.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Persistence adapters against a Testcontainers Postgres migrated by Flyway (V1 + V2 seed). */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class PersistenceAdaptersIT {

    private static final UUID UAI_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MARLEY = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired UserRepository userRepository;
    @Autowired TenantRepository tenantRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    @Test
    void readsSeededTenant() {
        Optional<Tenant> t = tenantRepository.findById(UAI_TENANT);
        assertThat(t).isPresent();
        assertThat(t.get().name()).isEqualTo("uAI");
        assertThat(t.get().active()).isTrue();
    }

    @Test
    void readsSeededAdminByEmail() {
        Optional<User> u = userRepository.findByEmail("marley.diniz@gmail.com");
        assertThat(u).isPresent();
        assertThat(u.get().role()).isEqualTo(Role.ADMIN);
        assertThat(u.get().enabled()).isTrue();
        assertThat(u.get().tenantId()).isEqualTo(UAI_TENANT);
    }

    @Test
    void updatesLastLogin() {
        Instant when = Instant.parse("2026-06-06T10:00:00Z");
        userRepository.updateLastLogin(MARLEY, when);
        assertThat(userRepository.findById(MARLEY)).get()
                .extracting(User::lastLoginAt).isEqualTo(when);
    }

    @Test
    void createsFindsAndRevokesARefreshToken() {
        String raw = "raw-refresh-" + UUID.randomUUID();
        RefreshToken created = refreshTokenRepository.create(
                MARLEY, raw, Instant.now().plusSeconds(3600));

        assertThat(refreshTokenRepository.findActiveByRawToken(raw)).isPresent();

        refreshTokenRepository.revoke(created.id());
        assertThat(refreshTokenRepository.findActiveByRawToken(raw)).isEmpty();
    }

    @Test
    void doesNotReturnExpiredRefreshToken() {
        String raw = "expired-" + UUID.randomUUID();
        refreshTokenRepository.create(MARLEY, raw, Instant.now().minusSeconds(10));
        assertThat(refreshTokenRepository.findActiveByRawToken(raw)).isEmpty();
    }
}
```
- [ ] Step: Run it, verify it FAILS — `mvn -q -Dtest=PersistenceAdaptersIT test` (this IT is named `*IT` but we run it via surefire's `-Dtest` to fail fast on compile) → compile error (entities/adapters/ports missing).
- [ ] Step: Create the Flyway V1 migration.
```sql
-- src/main/resources/db/migration/V1__init.sql
CREATE TABLE tenant (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_account (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      UUID NOT NULL REFERENCES tenant(id),
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255) NOT NULL,   -- bcrypt strength 12
    role           VARCHAR(50) NOT NULL,    -- ADMIN | AGENCY_MANAGER | CLIENT_VIEWER | SELF_SERVICE_USER
    enabled        BOOLEAN NOT NULL DEFAULT true,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at  TIMESTAMPTZ
);
CREATE INDEX idx_user_account_tenant ON user_account(tenant_id);
CREATE INDEX idx_user_account_email ON user_account(email);

CREATE TABLE refresh_token (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES user_account(id),
    token_hash  VARCHAR(255) NOT NULL UNIQUE,   -- SHA-256 hex of the opaque refresh token
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_token_user ON refresh_token(user_id);
```
- [ ] Step: Create the V2 seed (placeholders — real hashes filled by a USER step in Task 16).
```sql
-- src/main/resources/db/migration/V2__seed_tenant_and_admins.sql
--
-- Tenant "uAI" + two ADMIN users (decision 2026-06-06). Passwords were generated
-- OUT-OF-BAND; only the bcrypt(12) hash is seeded. Plaintext NEVER enters the repo.
--
-- BEFORE the first prod build, replace the two placeholders below with the real hashes
-- produced by tools/BcryptHashGeneratorTest (see README "Generating the seed hashes").
-- CI/tests stay green without this: no test authenticates as marley/vivian.
--
INSERT INTO tenant (id, name, active, created_at)
VALUES ('00000000-0000-0000-0000-000000000001', 'uAI', true, now());

INSERT INTO user_account (id, tenant_id, email, password_hash, role, enabled, created_at)
VALUES
  ('00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000001',
   'marley.diniz@gmail.com', '__BCRYPT12_HASH_MARLEY__', 'ADMIN', true, now()),
  ('00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-000000000001',
   'vivian.cristina@bhbusmidia.com.br', '__BCRYPT12_HASH_VIVIAN__', 'ADMIN', true, now());
```
- [ ] Step: Create domain records + ports.
```java
// src/main/java/com/uai/auth/domain/model/Tenant.java
package com.uai.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public record Tenant(UUID id, String name, boolean active, Instant createdAt) {
}
```
```java
// src/main/java/com/uai/auth/domain/model/RefreshToken.java
package com.uai.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public record RefreshToken(UUID id, UUID userId, Instant expiresAt, Instant revokedAt, Instant createdAt) {
}
```
```java
// src/main/java/com/uai/auth/domain/port/out/UserRepository.java
package com.uai.auth.domain.port.out;

import com.uai.auth.domain.model.User;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    Optional<User> findByEmail(String email);

    Optional<User> findById(UUID id);

    void updateLastLogin(UUID id, Instant at);
}
```
```java
// src/main/java/com/uai/auth/domain/port/out/TenantRepository.java
package com.uai.auth.domain.port.out;

import com.uai.auth.domain.model.Tenant;

import java.util.Optional;
import java.util.UUID;

public interface TenantRepository {
    Optional<Tenant> findById(UUID id);
}
```
```java
// src/main/java/com/uai/auth/domain/port/out/RefreshTokenRepository.java
package com.uai.auth.domain.port.out;

import com.uai.auth.domain.model.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
    RefreshToken create(UUID userId, String rawToken, Instant expiresAt);

    /** Active = exists, not revoked, not expired. Implementation hashes {@code rawToken}. */
    Optional<RefreshToken> findActiveByRawToken(String rawToken);

    void revoke(UUID refreshTokenId);
}
```
- [ ] Step: Create JPA entities (package-private fields, field access; same package as adapters so no getters needed).
```java
// src/main/java/com/uai/auth/adapter/out/persistence/TenantEntity.java
package com.uai.auth.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tenant")
class TenantEntity {

    @Id
    UUID id;

    @Column(nullable = false)
    String name;

    @Column(nullable = false)
    boolean active;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected TenantEntity() {
    }
}
```
```java
// src/main/java/com/uai/auth/adapter/out/persistence/UserAccountEntity.java
package com.uai.auth.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_account")
class UserAccountEntity {

    @Id
    UUID id;

    @Column(name = "tenant_id", nullable = false)
    UUID tenantId;

    @Column(nullable = false)
    String email;

    @Column(name = "password_hash", nullable = false)
    String passwordHash;

    @Column(nullable = false)
    String role;

    @Column(nullable = false)
    boolean enabled;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(name = "last_login_at")
    Instant lastLoginAt;

    protected UserAccountEntity() {
    }
}
```
```java
// src/main/java/com/uai/auth/adapter/out/persistence/RefreshTokenEntity.java
package com.uai.auth.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
class RefreshTokenEntity {

    @Id
    UUID id;

    @Column(name = "user_id", nullable = false)
    UUID userId;

    @Column(name = "token_hash", nullable = false)
    String tokenHash;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    @Column(name = "revoked_at")
    Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    protected RefreshTokenEntity() {
    }
}
```
- [ ] Step: Create Spring Data repositories.
```java
// src/main/java/com/uai/auth/adapter/out/persistence/TenantJpaRepository.java
package com.uai.auth.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface TenantJpaRepository extends JpaRepository<TenantEntity, UUID> {
}
```
```java
// src/main/java/com/uai/auth/adapter/out/persistence/UserAccountJpaRepository.java
package com.uai.auth.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, UUID> {
    Optional<UserAccountEntity> findByEmail(String email);
}
```
```java
// src/main/java/com/uai/auth/adapter/out/persistence/RefreshTokenJpaRepository.java
package com.uai.auth.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
}
```
- [ ] Step: Create the port adapters.
```java
// src/main/java/com/uai/auth/adapter/out/persistence/TenantRepositoryAdapter.java
package com.uai.auth.adapter.out.persistence;

import com.uai.auth.domain.model.Tenant;
import com.uai.auth.domain.port.out.TenantRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class TenantRepositoryAdapter implements TenantRepository {

    private final TenantJpaRepository jpa;

    TenantRepositoryAdapter(TenantJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Tenant> findById(UUID id) {
        return jpa.findById(id).map(e -> new Tenant(e.id, e.name, e.active, e.createdAt));
    }
}
```
```java
// src/main/java/com/uai/auth/adapter/out/persistence/UserRepositoryAdapter.java
package com.uai.auth.adapter.out.persistence;

import com.uai.auth.domain.model.Role;
import com.uai.auth.domain.model.User;
import com.uai.auth.domain.port.out.UserRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class UserRepositoryAdapter implements UserRepository {

    private final UserAccountJpaRepository jpa;

    UserRepositoryAdapter(UserAccountJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email).map(UserRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id).map(UserRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional
    public void updateLastLogin(UUID id, Instant at) {
        jpa.findById(id).ifPresent(e -> {
            e.lastLoginAt = at;
            jpa.save(e);
        });
    }

    private static User toDomain(UserAccountEntity e) {
        return new User(e.id, e.tenantId, e.email, e.passwordHash,
                Role.valueOf(e.role), e.enabled, e.createdAt, e.lastLoginAt);
    }
}
```
```java
// src/main/java/com/uai/auth/adapter/out/persistence/RefreshTokenRepositoryAdapter.java
package com.uai.auth.adapter.out.persistence;

import com.uai.auth.domain.model.RefreshToken;
import com.uai.auth.domain.port.out.RefreshTokenRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Repository
class RefreshTokenRepositoryAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository jpa;

    RefreshTokenRepositoryAdapter(RefreshTokenJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public RefreshToken create(UUID userId, String rawToken, Instant expiresAt) {
        RefreshTokenEntity e = new RefreshTokenEntity();
        e.id = UUID.randomUUID();
        e.userId = userId;
        e.tokenHash = sha256Hex(rawToken);
        e.expiresAt = expiresAt;
        e.revokedAt = null;
        e.createdAt = Instant.now();
        jpa.save(e);
        return toDomain(e);
    }

    @Override
    public Optional<RefreshToken> findActiveByRawToken(String rawToken) {
        return jpa.findByTokenHash(sha256Hex(rawToken))
                .filter(e -> e.revokedAt == null && e.expiresAt.isAfter(Instant.now()))
                .map(RefreshTokenRepositoryAdapter::toDomain);
    }

    @Override
    @Transactional
    public void revoke(UUID refreshTokenId) {
        jpa.findById(refreshTokenId).ifPresent(e -> {
            e.revokedAt = Instant.now();
            jpa.save(e);
        });
    }

    private static RefreshToken toDomain(RefreshTokenEntity e) {
        return new RefreshToken(e.id, e.userId, e.expiresAt, e.revokedAt, e.createdAt);
    }

    static String sha256Hex(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
```
- [ ] Step: Create the test profile config.
```yaml
# src/test/resources/application-test.yml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true

logging:
  level:
    root: WARN
    com.uai.auth: INFO
    org.testcontainers: WARN
    org.flywaydb: WARN

uai:
  auth:
    internal:
      api-key: test-internal-key
```
- [ ] Step: Run tests, verify PASS — `mvn -q verify -Djacoco.skip=true -Dit.test=PersistenceAdaptersIT -Dsurefire.failIfNoSpecifiedTests=false` → Failsafe runs `PersistenceAdaptersIT`, 5 tests pass (Flyway applies V1+V2, Hibernate `validate` confirms entity↔schema mapping).
- [ ] Step: Commit — `git add -A && git commit -m "feat(auth): Flyway V1 schema + JPA entities + persistence adapters (+ seed placeholders)"`

---

### Task 6: Redis blacklist adapter

**Files:** Create `domain/port/out/TokenBlacklist.java`, `adapter/out/redis/RedisTokenBlacklist.java`.
**Test:** `src/test/java/com/uai/auth/adapter/out/redis/RedisTokenBlacklistIT.java`

- [ ] Step: Write the failing integration test.
```java
// src/test/java/com/uai/auth/adapter/out/redis/RedisTokenBlacklistIT.java
package com.uai.auth.adapter.out.redis;

import com.uai.auth.domain.port.out.TokenBlacklist;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Redis jti-blacklist against a Testcontainers Redis (+ Postgres for the full context). */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class RedisTokenBlacklistIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired TokenBlacklist blacklist;

    @Test
    void unknownJtiIsNotBlacklisted() {
        assertThat(blacklist.isBlacklisted("nope-" + UUID.randomUUID())).isFalse();
    }

    @Test
    void blacklistedJtiIsReported() {
        String jti = UUID.randomUUID().toString();
        blacklist.blacklist(jti, Duration.ofMinutes(5));
        assertThat(blacklist.isBlacklisted(jti)).isTrue();
    }

    @Test
    void nonPositiveTtlIsANoOp() {
        String jti = UUID.randomUUID().toString();
        blacklist.blacklist(jti, Duration.ofSeconds(-1));
        assertThat(blacklist.isBlacklisted(jti)).isFalse();
    }
}
```
> Note: `@ServiceConnection` on a raw `GenericContainer("redis:...")` is recognized by Spring Boot's `RedisContainerConnectionDetailsFactory` (image name starts with `redis`), so no `@DynamicPropertySource` is needed.
- [ ] Step: Run it, verify it FAILS — `mvn -q verify -Djacoco.skip=true -Dit.test=RedisTokenBlacklistIT -Dsurefire.failIfNoSpecifiedTests=false` → compile error (`TokenBlacklist`/`RedisTokenBlacklist` missing).
- [ ] Step: Implement the port and adapter.
```java
// src/main/java/com/uai/auth/domain/port/out/TokenBlacklist.java
package com.uai.auth.domain.port.out;

import java.time.Duration;

public interface TokenBlacklist {
    /** Blacklist a jti for {@code ttl}. Non-positive ttl is a no-op (token already expired). */
    void blacklist(String jti, Duration ttl);

    boolean isBlacklisted(String jti);
}
```
```java
// src/main/java/com/uai/auth/adapter/out/redis/RedisTokenBlacklist.java
package com.uai.auth.adapter.out.redis;

import com.uai.auth.domain.port.out.TokenBlacklist;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class RedisTokenBlacklist implements TokenBlacklist {

    static final String PREFIX = "blacklist:jti:";

    private final StringRedisTemplate redis;

    public RedisTokenBlacklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void blacklist(String jti, Duration ttl) {
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        redis.opsForValue().set(PREFIX + jti, Instant.now().toString(), ttl);
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + jti));
    }
}
```
- [ ] Step: Run tests, verify PASS — `mvn -q verify -Djacoco.skip=true -Dit.test=RedisTokenBlacklistIT -Dsurefire.failIfNoSpecifiedTests=false` → 3 tests pass.
- [ ] Step: Commit — `git add -A && git commit -m "feat(auth): Redis jti-blacklist adapter (blacklist:jti:{jti}, ttl)"`

---

### Task 7: Login use case

**Files:** Create `domain/model/TokenPair.java`, `domain/model/InvalidCredentialsException.java`, `domain/port/in/LoginUseCase.java`, `application/usecase/LoginService.java`.
**Test:** `src/test/java/com/uai/auth/application/usecase/LoginServiceTest.java`

- [ ] Step: Write the failing test.
```java
// src/test/java/com/uai/auth/application/usecase/LoginServiceTest.java
package com.uai.auth.application.usecase;

import com.uai.auth.adapter.out.token.JwtProperties;
import com.uai.auth.domain.model.*;
import com.uai.auth.domain.port.out.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LoginServiceTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(12);
    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final TokenIssuer issuer = mock(TokenIssuer.class);
    private final Instant now = Instant.parse("2026-06-06T12:00:00Z");
    private final JwtProperties props =
            new JwtProperties("uai-auth", "uai-auth", Duration.ofMinutes(15), Duration.ofDays(7), "", "");
    private final LoginService service = new LoginService(
            users, refreshTokens, issuer, encoder, props, Clock.fixed(now, ZoneOffset.UTC));

    private final UUID userId = UUID.randomUUID();

    private User enabledUser(String hash) {
        return new User(userId, UUID.randomUUID(), "v@uai.test", hash, Role.ADMIN, true, Instant.EPOCH, null);
    }

    @Test
    void issuesTokensOnValidCredentialsAndStampsLastLogin() {
        User u = enabledUser(encoder.encode("secret"));
        when(users.findByEmail("v@uai.test")).thenReturn(Optional.of(u));
        when(issuer.issueAccessToken(u)).thenReturn(new IssuedAccessToken("access.jwt", "jti-1", now));
        when(issuer.issueRefreshToken()).thenReturn(new IssuedRefreshToken("raw-refresh", now));

        TokenPair pair = service.login("v@uai.test", "secret");

        assertThat(pair.accessToken()).isEqualTo("access.jwt");
        assertThat(pair.refreshToken()).isEqualTo("raw-refresh");
        assertThat(pair.expiresInSeconds()).isEqualTo(900);
        verify(refreshTokens).create(eq(userId), eq("raw-refresh"), any());
        verify(users).updateLastLogin(userId, now);
    }

    @Test
    void rejectsUnknownEmail() {
        when(users.findByEmail("ghost@uai.test")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login("ghost@uai.test", "x"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void rejectsWrongPassword() {
        when(users.findByEmail("v@uai.test")).thenReturn(Optional.of(enabledUser(encoder.encode("secret"))));
        assertThatThrownBy(() -> service.login("v@uai.test", "WRONG"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(issuer, never()).issueAccessToken(any());
    }

    @Test
    void rejectsDisabledUser() {
        User disabled = new User(userId, UUID.randomUUID(), "v@uai.test",
                encoder.encode("secret"), Role.ADMIN, false, Instant.EPOCH, null);
        when(users.findByEmail("v@uai.test")).thenReturn(Optional.of(disabled));
        assertThatThrownBy(() -> service.login("v@uai.test", "secret"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
```
- [ ] Step: Run it, verify it FAILS — `mvn -q -Dtest=LoginServiceTest test` → compile error (`TokenPair`/`LoginService`/etc. missing).
- [ ] Step: Implement domain + port + service.
```java
// src/main/java/com/uai/auth/domain/model/TokenPair.java
package com.uai.auth.domain.model;

public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {
}
```
```java
// src/main/java/com/uai/auth/domain/model/InvalidCredentialsException.java
package com.uai.auth.domain.model;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("invalid credentials");
    }
}
```
```java
// src/main/java/com/uai/auth/domain/port/in/LoginUseCase.java
package com.uai.auth.domain.port.in;

import com.uai.auth.domain.model.TokenPair;

public interface LoginUseCase {
    TokenPair login(String email, String password);
}
```
```java
// src/main/java/com/uai/auth/application/usecase/LoginService.java
package com.uai.auth.application.usecase;

import com.uai.auth.adapter.out.token.JwtProperties;
import com.uai.auth.domain.model.IssuedAccessToken;
import com.uai.auth.domain.model.IssuedRefreshToken;
import com.uai.auth.domain.model.InvalidCredentialsException;
import com.uai.auth.domain.model.TokenPair;
import com.uai.auth.domain.model.User;
import com.uai.auth.domain.port.in.LoginUseCase;
import com.uai.auth.domain.port.out.RefreshTokenRepository;
import com.uai.auth.domain.port.out.TokenIssuer;
import com.uai.auth.domain.port.out.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class LoginService implements LoginUseCase {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final TokenIssuer issuer;
    private final PasswordEncoder encoder;
    private final JwtProperties props;
    private final Clock clock;

    public LoginService(UserRepository users, RefreshTokenRepository refreshTokens, TokenIssuer issuer,
                        PasswordEncoder encoder, JwtProperties props, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.issuer = issuer;
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public TokenPair login(String email, String password) {
        User user = users.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
        if (!user.enabled() || !encoder.matches(password, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        IssuedAccessToken access = issuer.issueAccessToken(user);
        IssuedRefreshToken refresh = issuer.issueRefreshToken();
        refreshTokens.create(user.id(), refresh.rawToken(), refresh.expiresAt());
        users.updateLastLogin(user.id(), clock.instant());
        return new TokenPair(access.token(), refresh.rawToken(), props.accessTtl().toSeconds());
    }
}
```
- [ ] Step: Run tests, verify PASS — `mvn -q -Dtest=LoginServiceTest test` → 4 tests pass.
- [ ] Step: Commit — `git add -A && git commit -m "feat(auth): login use case (BCrypt verify, token pair, last-login stamp)"`

---

### Task 8: Refresh use case (rotation)

**Files:** Create `domain/model/InvalidRefreshTokenException.java`, `domain/port/in/RefreshUseCase.java`, `application/usecase/RefreshService.java`.
**Test:** `src/test/java/com/uai/auth/application/usecase/RefreshServiceTest.java`

- [ ] Step: Write the failing test.
```java
// src/test/java/com/uai/auth/application/usecase/RefreshServiceTest.java
package com.uai.auth.application.usecase;

import com.uai.auth.adapter.out.token.JwtProperties;
import com.uai.auth.domain.model.*;
import com.uai.auth.domain.port.out.*;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RefreshServiceTest {

    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TokenIssuer issuer = mock(TokenIssuer.class);
    private final JwtProperties props =
            new JwtProperties("uai-auth", "uai-auth", Duration.ofMinutes(15), Duration.ofDays(7), "", "");
    private final RefreshService service = new RefreshService(refreshTokens, users, issuer, props);

    private final UUID userId = UUID.randomUUID();
    private final UUID rtId = UUID.randomUUID();

    @Test
    void rotatesTokensAndRevokesTheOldRefresh() {
        RefreshToken existing = new RefreshToken(rtId, userId, Instant.now().plusSeconds(3600), null, Instant.now());
        User u = new User(userId, UUID.randomUUID(), "v@uai.test", "$2a$12$h", Role.ADMIN, true, Instant.EPOCH, null);
        when(refreshTokens.findActiveByRawToken("old-raw")).thenReturn(Optional.of(existing));
        when(users.findById(userId)).thenReturn(Optional.of(u));
        when(issuer.issueAccessToken(u)).thenReturn(new IssuedAccessToken("new.access", "jti-2", Instant.now()));
        when(issuer.issueRefreshToken()).thenReturn(new IssuedRefreshToken("new-raw", Instant.now()));

        TokenPair pair = service.refresh("old-raw");

        assertThat(pair.accessToken()).isEqualTo("new.access");
        assertThat(pair.refreshToken()).isEqualTo("new-raw");
        verify(refreshTokens).revoke(rtId);
        verify(refreshTokens).create(eq(userId), eq("new-raw"), any());
    }

    @Test
    void rejectsUnknownOrRevokedRefreshToken() {
        when(refreshTokens.findActiveByRawToken("bad")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.refresh("bad")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void rejectsWhenUserDisabled() {
        RefreshToken existing = new RefreshToken(rtId, userId, Instant.now().plusSeconds(3600), null, Instant.now());
        User disabled = new User(userId, UUID.randomUUID(), "v@uai.test", "$2a$12$h", Role.ADMIN, false, Instant.EPOCH, null);
        when(refreshTokens.findActiveByRawToken("old")).thenReturn(Optional.of(existing));
        when(users.findById(userId)).thenReturn(Optional.of(disabled));
        assertThatThrownBy(() -> service.refresh("old")).isInstanceOf(InvalidRefreshTokenException.class);
    }
}
```
- [ ] Step: Run it, verify it FAILS — `mvn -q -Dtest=RefreshServiceTest test` → compile error.
- [ ] Step: Implement.
```java
// src/main/java/com/uai/auth/domain/model/InvalidRefreshTokenException.java
package com.uai.auth.domain.model;

public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("invalid refresh token");
    }
}
```
```java
// src/main/java/com/uai/auth/domain/port/in/RefreshUseCase.java
package com.uai.auth.domain.port.in;

import com.uai.auth.domain.model.TokenPair;

public interface RefreshUseCase {
    TokenPair refresh(String refreshToken);
}
```
```java
// src/main/java/com/uai/auth/application/usecase/RefreshService.java
package com.uai.auth.application.usecase;

import com.uai.auth.adapter.out.token.JwtProperties;
import com.uai.auth.domain.model.IssuedAccessToken;
import com.uai.auth.domain.model.IssuedRefreshToken;
import com.uai.auth.domain.model.InvalidRefreshTokenException;
import com.uai.auth.domain.model.RefreshToken;
import com.uai.auth.domain.model.TokenPair;
import com.uai.auth.domain.model.User;
import com.uai.auth.domain.port.in.RefreshUseCase;
import com.uai.auth.domain.port.out.RefreshTokenRepository;
import com.uai.auth.domain.port.out.TokenIssuer;
import com.uai.auth.domain.port.out.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class RefreshService implements RefreshUseCase {

    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final TokenIssuer issuer;
    private final JwtProperties props;

    public RefreshService(RefreshTokenRepository refreshTokens, UserRepository users,
                          TokenIssuer issuer, JwtProperties props) {
        this.refreshTokens = refreshTokens;
        this.users = users;
        this.issuer = issuer;
        this.props = props;
    }

    @Override
    public TokenPair refresh(String refreshToken) {
        RefreshToken existing = refreshTokens.findActiveByRawToken(refreshToken)
                .orElseThrow(InvalidRefreshTokenException::new);
        User user = users.findById(existing.userId())
                .filter(User::enabled)
                .orElseThrow(InvalidRefreshTokenException::new);

        refreshTokens.revoke(existing.id());

        IssuedAccessToken access = issuer.issueAccessToken(user);
        IssuedRefreshToken refresh = issuer.issueRefreshToken();
        refreshTokens.create(user.id(), refresh.rawToken(), refresh.expiresAt());
        return new TokenPair(access.token(), refresh.rawToken(), props.accessTtl().toSeconds());
    }
}
```
- [ ] Step: Run tests, verify PASS — `mvn -q -Dtest=RefreshServiceTest test` → 3 tests pass.
- [ ] Step: Commit — `git add -A && git commit -m "feat(auth): refresh use case with rotation (old refresh revoked)"`

---

### Task 9: Logout + Revoke use cases

**Files:** Create `domain/port/in/LogoutUseCase.java`, `domain/port/in/RevokeUseCase.java`, `application/usecase/LogoutService.java`, `application/usecase/RevokeService.java`.
**Test:** `src/test/java/com/uai/auth/application/usecase/LogoutServiceTest.java`, `RevokeServiceTest.java`

- [ ] Step: Write the failing logout test.
```java
// src/test/java/com/uai/auth/application/usecase/LogoutServiceTest.java
package com.uai.auth.application.usecase;

import com.uai.auth.domain.port.out.TokenBlacklist;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LogoutServiceTest {

    private final TokenBlacklist blacklist = mock(TokenBlacklist.class);
    private final Instant now = Instant.parse("2026-06-06T12:00:00Z");
    private final LogoutService service = new LogoutService(blacklist, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void blacklistsJtiForTheRemainingLifetime() {
        Instant exp = now.plus(Duration.ofMinutes(10));
        service.logout("jti-9", exp);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(blacklist).blacklist(eq("jti-9"), ttl.capture());
        assertThat(ttl.getValue()).isEqualTo(Duration.ofMinutes(10));
    }
}
```
- [ ] Step: Run it, verify it FAILS — `mvn -q -Dtest=LogoutServiceTest test` → compile error.
- [ ] Step: Implement logout port + service.
```java
// src/main/java/com/uai/auth/domain/port/in/LogoutUseCase.java
package com.uai.auth.domain.port.in;

import java.time.Instant;

public interface LogoutUseCase {
    void logout(String jti, Instant accessExpiresAt);
}
```
```java
// src/main/java/com/uai/auth/application/usecase/LogoutService.java
package com.uai.auth.application.usecase;

import com.uai.auth.domain.port.in.LogoutUseCase;
import com.uai.auth.domain.port.out.TokenBlacklist;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Service
public class LogoutService implements LogoutUseCase {

    private final TokenBlacklist blacklist;
    private final Clock clock;

    public LogoutService(TokenBlacklist blacklist, Clock clock) {
        this.blacklist = blacklist;
        this.clock = clock;
    }

    @Override
    public void logout(String jti, Instant accessExpiresAt) {
        blacklist.blacklist(jti, Duration.between(clock.instant(), accessExpiresAt));
    }
}
```
- [ ] Step: Run tests, verify PASS — `mvn -q -Dtest=LogoutServiceTest test` → 1 test passes.
- [ ] Step: Write the failing revoke test.
```java
// src/test/java/com/uai/auth/application/usecase/RevokeServiceTest.java
package com.uai.auth.application.usecase;

import com.uai.auth.adapter.out.token.JwtProperties;
import com.uai.auth.domain.port.out.TokenBlacklist;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.Mockito.*;

class RevokeServiceTest {

    private final TokenBlacklist blacklist = mock(TokenBlacklist.class);
    private final JwtProperties props =
            new JwtProperties("uai-auth", "uai-auth", Duration.ofMinutes(15), Duration.ofDays(7), "", "");
    private final RevokeService service = new RevokeService(blacklist, props);

    @Test
    void blacklistsTheJtiForTheAccessTtlUpperBound() {
        service.revoke("jti-x");
        verify(blacklist).blacklist("jti-x", Duration.ofMinutes(15));
    }
}
```
- [ ] Step: Run it, verify it FAILS — `mvn -q -Dtest=RevokeServiceTest test` → compile error.
- [ ] Step: Implement revoke port + service.
```java
// src/main/java/com/uai/auth/domain/port/in/RevokeUseCase.java
package com.uai.auth.domain.port.in;

public interface RevokeUseCase {
    void revoke(String jti);
}
```
```java
// src/main/java/com/uai/auth/application/usecase/RevokeService.java
package com.uai.auth.application.usecase;

import com.uai.auth.adapter.out.token.JwtProperties;
import com.uai.auth.domain.port.in.RevokeUseCase;
import com.uai.auth.domain.port.out.TokenBlacklist;
import org.springframework.stereotype.Service;

/**
 * Revoke by jti. The caller supplies only a jti (no exp), so we blacklist for the access-token
 * TTL upper bound — the underlying token cannot outlive that, so coverage is guaranteed.
 */
@Service
public class RevokeService implements RevokeUseCase {

    private final TokenBlacklist blacklist;
    private final JwtProperties props;

    public RevokeService(TokenBlacklist blacklist, JwtProperties props) {
        this.blacklist = blacklist;
        this.props = props;
    }

    @Override
    public void revoke(String jti) {
        blacklist.blacklist(jti, props.accessTtl());
    }
}
```
- [ ] Step: Run tests, verify PASS — `mvn -q -Dtest=RevokeServiceTest test` → 1 test passes.
- [ ] Step: Commit — `git add -A && git commit -m "feat(auth): logout + revoke use cases (jti blacklist)"`

---

### Task 10: Introspect use case

**Files:** Create `domain/model/IntrospectionResult.java`, `domain/port/in/IntrospectUseCase.java`, `application/usecase/IntrospectService.java`.
**Test:** `src/test/java/com/uai/auth/application/usecase/IntrospectServiceTest.java`

- [ ] Step: Write the failing test.
```java
// src/test/java/com/uai/auth/application/usecase/IntrospectServiceTest.java
package com.uai.auth.application.usecase;

import com.uai.auth.domain.model.*;
import com.uai.auth.domain.port.out.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class IntrospectServiceTest {

    private final TokenVerifier verifier = mock(TokenVerifier.class);
    private final TokenBlacklist blacklist = mock(TokenBlacklist.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TenantRepository tenants = mock(TenantRepository.class);
    private final IntrospectService service = new IntrospectService(verifier, blacklist, users, tenants);

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final Instant exp = Instant.parse("2026-06-06T12:15:00Z");

    private AccessClaims claims() {
        return new AccessClaims(userId, tenantId, Role.ADMIN, "v@uai.test", "jti-1", exp);
    }

    private void wireHappyPath() {
        when(verifier.verify("good")).thenReturn(claims());
        when(blacklist.isBlacklisted("jti-1")).thenReturn(false);
        when(users.findById(userId)).thenReturn(Optional.of(
                new User(userId, tenantId, "v@uai.test", "$2a$12$h", Role.ADMIN, true, Instant.EPOCH, null)));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant(tenantId, "uAI", true, Instant.EPOCH)));
    }

    @Test
    void activeForAValidTokenEnabledUserActiveTenant() {
        wireHappyPath();
        IntrospectionResult r = service.introspect("good");
        assertThat(r.active()).isTrue();
        assertThat(r.userId()).isEqualTo(userId);
        assertThat(r.email()).isEqualTo("v@uai.test");
        assertThat(r.tenantId()).isEqualTo(tenantId);
        assertThat(r.role()).isEqualTo(Role.ADMIN);
        assertThat(r.expiresAt()).isEqualTo(exp);
    }

    @Test
    void inactiveWhenSignatureInvalid() {
        when(verifier.verify("bad")).thenThrow(new TokenInvalidException("x", null));
        assertThat(service.introspect("bad").active()).isFalse();
    }

    @Test
    void inactiveWhenBlacklisted() {
        when(verifier.verify("good")).thenReturn(claims());
        when(blacklist.isBlacklisted("jti-1")).thenReturn(true);
        assertThat(service.introspect("good").active()).isFalse();
    }

    @Test
    void inactiveWhenUserDisabledOrMissing() {
        when(verifier.verify("good")).thenReturn(claims());
        when(blacklist.isBlacklisted("jti-1")).thenReturn(false);
        when(users.findById(userId)).thenReturn(Optional.of(
                new User(userId, tenantId, "v@uai.test", "$2a$12$h", Role.ADMIN, false, Instant.EPOCH, null)));
        assertThat(service.introspect("good").active()).isFalse();
    }

    @Test
    void inactiveWhenTenantInactive() {
        when(verifier.verify("good")).thenReturn(claims());
        when(blacklist.isBlacklisted("jti-1")).thenReturn(false);
        when(users.findById(userId)).thenReturn(Optional.of(
                new User(userId, tenantId, "v@uai.test", "$2a$12$h", Role.ADMIN, true, Instant.EPOCH, null)));
        when(tenants.findById(tenantId)).thenReturn(Optional.of(new Tenant(tenantId, "uAI", false, Instant.EPOCH)));
        assertThat(service.introspect("good").active()).isFalse();
    }
}
```
- [ ] Step: Run it, verify it FAILS — `mvn -q -Dtest=IntrospectServiceTest test` → compile error.
- [ ] Step: Implement.
```java
// src/main/java/com/uai/auth/domain/model/IntrospectionResult.java
package com.uai.auth.domain.model;

import java.time.Instant;
import java.util.UUID;

public record IntrospectionResult(
        boolean active, UUID userId, String email, UUID tenantId, Role role, Instant expiresAt) {

    public static IntrospectionResult inactive() {
        return new IntrospectionResult(false, null, null, null, null, null);
    }

    public static IntrospectionResult active(UUID userId, String email, UUID tenantId, Role role, Instant expiresAt) {
        return new IntrospectionResult(true, userId, email, tenantId, role, expiresAt);
    }
}
```
```java
// src/main/java/com/uai/auth/domain/port/in/IntrospectUseCase.java
package com.uai.auth.domain.port.in;

import com.uai.auth.domain.model.IntrospectionResult;

public interface IntrospectUseCase {
    IntrospectionResult introspect(String token);
}
```
```java
// src/main/java/com/uai/auth/application/usecase/IntrospectService.java
package com.uai.auth.application.usecase;

import com.uai.auth.domain.model.AccessClaims;
import com.uai.auth.domain.model.IntrospectionResult;
import com.uai.auth.domain.model.Tenant;
import com.uai.auth.domain.model.TokenInvalidException;
import com.uai.auth.domain.model.User;
import com.uai.auth.domain.port.in.IntrospectUseCase;
import com.uai.auth.domain.port.out.TenantRepository;
import com.uai.auth.domain.port.out.TokenBlacklist;
import com.uai.auth.domain.port.out.TokenVerifier;
import com.uai.auth.domain.port.out.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class IntrospectService implements IntrospectUseCase {

    private final TokenVerifier verifier;
    private final TokenBlacklist blacklist;
    private final UserRepository users;
    private final TenantRepository tenants;

    public IntrospectService(TokenVerifier verifier, TokenBlacklist blacklist,
                             UserRepository users, TenantRepository tenants) {
        this.verifier = verifier;
        this.blacklist = blacklist;
        this.users = users;
        this.tenants = tenants;
    }

    @Override
    public IntrospectionResult introspect(String token) {
        AccessClaims claims;
        try {
            claims = verifier.verify(token);   // also rejects expired tokens
        } catch (TokenInvalidException e) {
            return IntrospectionResult.inactive();
        }
        if (blacklist.isBlacklisted(claims.jti())) {
            return IntrospectionResult.inactive();
        }
        Optional<User> user = users.findById(claims.userId()).filter(User::enabled);
        if (user.isEmpty()) {
            return IntrospectionResult.inactive();
        }
        User u = user.get();
        Optional<Tenant> tenant = tenants.findById(u.tenantId()).filter(Tenant::active);
        if (tenant.isEmpty()) {
            return IntrospectionResult.inactive();
        }
        return IntrospectionResult.active(u.id(), u.email(), u.tenantId(), u.role(), claims.expiresAt());
    }
}
```
- [ ] Step: Run tests, verify PASS — `mvn -q -Dtest=IntrospectServiceTest test` → 5 tests pass.
- [ ] Step: Commit — `git add -A && git commit -m "feat(auth): introspect use case (signature + blacklist + user/tenant checks)"`

---

### Task 11: Web DTOs + controllers + exception handler

**Files:** Create `adapter/in/web/dto/{LoginRequest,RefreshRequest,IntrospectRequest,RevokeRequest,TokenResponse,MeResponse,IntrospectResponse}.java`; `adapter/in/web/security/AuthPrincipal.java`; `adapter/in/web/{AuthController,IntrospectionController,RevokeController,JwksController,ApiExceptionHandler}.java`.
**Test:** `src/test/java/com/uai/auth/adapter/in/web/AuthControllerTest.java` (standalone MockMvc — no Spring context, no security)

- [ ] Step: Write the failing controller test.
```java
// src/test/java/com/uai/auth/adapter/in/web/AuthControllerTest.java
package com.uai.auth.adapter.in.web;

import com.uai.auth.domain.model.InvalidCredentialsException;
import com.uai.auth.domain.model.TokenPair;
import com.uai.auth.domain.port.in.LoginUseCase;
import com.uai.auth.domain.port.in.LogoutUseCase;
import com.uai.auth.domain.port.in.RefreshUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private final LoginUseCase login = mock(LoginUseCase.class);
    private final RefreshUseCase refresh = mock(RefreshUseCase.class);
    private final LogoutUseCase logout = mock(LogoutUseCase.class);

    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @BeforeEach
    void setUp() {
        var converter = new MappingJackson2HttpMessageConverter(mapper);
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(login, refresh, logout))
                .setControllerAdvice(new ApiExceptionHandler())
                .setMessageConverters(converter)
                .build();
    }

    @Test
    void loginReturnsSnakeCaseTokenPair() throws Exception {
        when(login.login("v@uai.test", "secret")).thenReturn(new TokenPair("acc.jwt", "ref-raw", 900));
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"v@uai.test\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("acc.jwt"))
                .andExpect(jsonPath("$.refresh_token").value("ref-raw"))
                .andExpect(jsonPath("$.expires_in").value(900));
    }

    @Test
    void loginWithBadCredentialsReturns401() throws Exception {
        when(login.login(eq("v@uai.test"), any())).thenThrow(new InvalidCredentialsException());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"v@uai.test\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void refreshRotatesTokens() throws Exception {
        when(refresh.refresh("old-raw")).thenReturn(new TokenPair("new.acc", "new-raw", 900));
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refresh_token\":\"old-raw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("new.acc"))
                .andExpect(jsonPath("$.refresh_token").value("new-raw"));
    }
}
```
- [ ] Step: Run it, verify it FAILS — `mvn -q -Dtest=AuthControllerTest test` → compile error (DTOs/controllers/advice missing).
- [ ] Step: Create DTOs.
```java
// src/main/java/com/uai/auth/adapter/in/web/dto/LoginRequest.java
package com.uai.auth.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
}
```
```java
// src/main/java/com/uai/auth/adapter/in/web/dto/RefreshRequest.java
package com.uai.auth.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

// JSON field is refresh_token (global SNAKE_CASE naming strategy).
public record RefreshRequest(@NotBlank String refreshToken) {
}
```
```java
// src/main/java/com/uai/auth/adapter/in/web/dto/IntrospectRequest.java
package com.uai.auth.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record IntrospectRequest(@NotBlank String token) {
}
```
```java
// src/main/java/com/uai/auth/adapter/in/web/dto/RevokeRequest.java
package com.uai.auth.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record RevokeRequest(@NotBlank String jti) {
}
```
```java
// src/main/java/com/uai/auth/adapter/in/web/dto/TokenResponse.java
package com.uai.auth.adapter.in.web.dto;

// Serializes as access_token / refresh_token / expires_in (SNAKE_CASE).
public record TokenResponse(String accessToken, String refreshToken, long expiresIn) {
}
```
```java
// src/main/java/com/uai/auth/adapter/in/web/dto/MeResponse.java
package com.uai.auth.adapter.in.web.dto;

import com.uai.auth.domain.model.Role;

import java.util.UUID;

// Serializes as user_id / email / role / tenant_id (SNAKE_CASE).
public record MeResponse(UUID userId, String email, Role role, UUID tenantId) {
}
```
```java
// src/main/java/com/uai/auth/adapter/in/web/dto/IntrospectResponse.java
package com.uai.auth.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.uai.auth.domain.model.IntrospectionResult;
import com.uai.auth.domain.model.Role;

import java.time.Instant;
import java.util.UUID;

// active:false omits the rest (JsonInclude NON_NULL). Fields → snake_case.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntrospectResponse(
        boolean active, UUID userId, String email, UUID tenantId, Role role, Instant expiresAt) {

    public static IntrospectResponse from(IntrospectionResult r) {
        if (!r.active()) {
            return new IntrospectResponse(false, null, null, null, null, null);
        }
        return new IntrospectResponse(true, r.userId(), r.email(), r.tenantId(), r.role(), r.expiresAt());
    }
}
```
- [ ] Step: Create the `AuthPrincipal` (used by controllers + filter).
```java
// src/main/java/com/uai/auth/adapter/in/web/security/AuthPrincipal.java
package com.uai.auth.adapter.in.web.security;

import com.uai.auth.domain.model.Role;

import java.time.Instant;
import java.util.UUID;

/** Authenticated principal placed in the SecurityContext by {@code JwtAuthenticationFilter}. */
public record AuthPrincipal(UUID userId, String email, UUID tenantId, Role role, String jti, Instant expiresAt) {
}
```
- [ ] Step: Create controllers + exception handler.
```java
// src/main/java/com/uai/auth/adapter/in/web/AuthController.java
package com.uai.auth.adapter.in.web;

import com.uai.auth.adapter.in.web.dto.LoginRequest;
import com.uai.auth.adapter.in.web.dto.MeResponse;
import com.uai.auth.adapter.in.web.dto.RefreshRequest;
import com.uai.auth.adapter.in.web.dto.TokenResponse;
import com.uai.auth.adapter.in.web.security.AuthPrincipal;
import com.uai.auth.domain.model.TokenPair;
import com.uai.auth.domain.port.in.LoginUseCase;
import com.uai.auth.domain.port.in.LogoutUseCase;
import com.uai.auth.domain.port.in.RefreshUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RefreshUseCase refreshUseCase;
    private final LogoutUseCase logoutUseCase;

    public AuthController(LoginUseCase loginUseCase, RefreshUseCase refreshUseCase, LogoutUseCase logoutUseCase) {
        this.loginUseCase = loginUseCase;
        this.refreshUseCase = refreshUseCase;
        this.logoutUseCase = logoutUseCase;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest req) {
        return toResponse(loginUseCase.login(req.email(), req.password()));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return toResponse(refreshUseCase.refresh(req.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
        logoutUseCase.logout(principal.jti(), principal.expiresAt());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        return new MeResponse(principal.userId(), principal.email(), principal.role(), principal.tenantId());
    }

    private static TokenResponse toResponse(TokenPair pair) {
        return new TokenResponse(pair.accessToken(), pair.refreshToken(), pair.expiresInSeconds());
    }
}
```
```java
// src/main/java/com/uai/auth/adapter/in/web/IntrospectionController.java
package com.uai.auth.adapter.in.web;

import com.uai.auth.adapter.in.web.dto.IntrospectRequest;
import com.uai.auth.adapter.in.web.dto.IntrospectResponse;
import com.uai.auth.domain.port.in.IntrospectUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal — guarded by {@code ApiKeyAuthFilter} (X-UAI-Internal-Key). */
@RestController
@RequestMapping("/api/v1/auth")
public class IntrospectionController {

    private final IntrospectUseCase introspectUseCase;

    public IntrospectionController(IntrospectUseCase introspectUseCase) {
        this.introspectUseCase = introspectUseCase;
    }

    @PostMapping("/introspect")
    public IntrospectResponse introspect(@Valid @RequestBody IntrospectRequest req) {
        return IntrospectResponse.from(introspectUseCase.introspect(req.token()));
    }
}
```
```java
// src/main/java/com/uai/auth/adapter/in/web/RevokeController.java
package com.uai.auth.adapter.in.web;

import com.uai.auth.adapter.in.web.dto.RevokeRequest;
import com.uai.auth.domain.port.in.RevokeUseCase;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal — guarded by {@code ApiKeyAuthFilter} (X-UAI-Internal-Key) AND ROLE_ADMIN bearer. */
@RestController
@RequestMapping("/api/v1/auth")
public class RevokeController {

    private final RevokeUseCase revokeUseCase;

    public RevokeController(RevokeUseCase revokeUseCase) {
        this.revokeUseCase = revokeUseCase;
    }

    @PostMapping("/revoke")
    public ResponseEntity<Void> revoke(@Valid @RequestBody RevokeRequest req) {
        revokeUseCase.revoke(req.jti());
        return ResponseEntity.noContent().build();
    }
}
```
```java
// src/main/java/com/uai/auth/adapter/in/web/JwksController.java
package com.uai.auth.adapter.in.web;

import com.uai.auth.adapter.out.token.JwtProperties;
import com.uai.auth.adapter.out.token.RsaKeyProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Public JWKS — exposes the RS256 public key (RFC 7517). */
@RestController
public class JwksController {

    private final RsaKeyProvider keys;
    private final JwtProperties props;

    public JwksController(RsaKeyProvider keys, JwtProperties props) {
        this.keys = keys;
        this.props = props;
    }

    @GetMapping("/.well-known/jwks")
    public Map<String, Object> jwks() {
        RSAPublicKey pub = keys.publicKey();
        return Map.of("keys", List.of(Map.of(
                "kty", "RSA",
                "use", "sig",
                "alg", "RS256",
                "kid", props.kid(),
                "n", b64url(toUnsigned(pub.getModulus())),
                "e", b64url(toUnsigned(pub.getPublicExponent())))));
    }

    private static byte[] toUnsigned(BigInteger v) {
        byte[] b = v.toByteArray();
        if (b.length > 1 && b[0] == 0) {
            byte[] trimmed = new byte[b.length - 1];
            System.arraycopy(b, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return b;
    }

    private static String b64url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
```
```java
// src/main/java/com/uai/auth/adapter/in/web/ApiExceptionHandler.java
package com.uai.auth.adapter.in.web;

import com.uai.auth.domain.model.InvalidCredentialsException;
import com.uai.auth.domain.model.InvalidRefreshTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<Map<String, Object>> handleBadCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_credentials"));
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    ResponseEntity<Map<String, Object>> handleBadRefresh(InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid_token"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "validation_failed"));
    }
}
```
- [ ] Step: Run tests, verify PASS — `mvn -q -Dtest=AuthControllerTest test` → 3 tests pass.
- [ ] Step: Commit — `git add -A && git commit -m "feat(auth): web DTOs + auth/introspect/revoke/jwks controllers + error handler"`

---

### Task 12: Spring Security (stateless JWT filter + ApiKey filter)

**Files:** Create `adapter/in/web/security/JwtAuthenticationFilter.java`, `adapter/in/web/security/ApiKeyAuthFilter.java`, `adapter/in/web/security/SecurityConfig.java`.
**Test:** covered end-to-end by Task 13's `AuthFlowIT` (security wiring requires the full context — Postgres + Redis + the real chain). This task has no isolated test; verify by compiling and the Task 13 IT.

- [ ] Step: Implement `JwtAuthenticationFilter`.
```java
// src/main/java/com/uai/auth/adapter/in/web/security/JwtAuthenticationFilter.java
package com.uai.auth.adapter.in.web.security;

import com.uai.auth.domain.model.AccessClaims;
import com.uai.auth.domain.model.TokenInvalidException;
import com.uai.auth.domain.port.out.TokenBlacklist;
import com.uai.auth.domain.port.out.TokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Verifies the service's own access JWT (RS256) for Bearer requests and populates the
 * SecurityContext with an {@link AuthPrincipal} + {@code ROLE_<role>} authority.
 *
 * <p>No header → pass through (authorization rules decide). Token present but invalid/expired/
 * blacklisted → 401 immediately.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String BEARER_PREFIX = "Bearer ";

    private final TokenVerifier verifier;
    private final TokenBlacklist blacklist;

    public JwtAuthenticationFilter(TokenVerifier verifier, TokenBlacklist blacklist) {
        this.verifier = verifier;
        this.blacklist = blacklist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            AccessClaims claims = verifier.verify(token);
            if (blacklist.isBlacklisted(claims.jti())) {
                unauthorized(response);
                return;
            }
            AuthPrincipal principal = new AuthPrincipal(
                    claims.userId(), claims.email(), claims.tenantId(), claims.role(),
                    claims.jti(), claims.expiresAt());
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority(claims.role().authority())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (TokenInvalidException e) {
            SecurityContextHolder.clearContext();
            unauthorized(response);
            return;
        }
        chain.doFilter(request, response);
    }

    private static void unauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"invalid_token\"}");
    }
}
```
- [ ] Step: Implement `ApiKeyAuthFilter` (timing-safe).
```java
// src/main/java/com/uai/auth/adapter/in/web/security/ApiKeyAuthFilter.java
package com.uai.auth.adapter.in.web.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Guards the internal auth endpoints with the {@code X-UAI-Internal-Key} header (ADR-040).
 * Comparison is timing-safe ({@link MessageDigest#isEqual}). Blank configured key ⇒ reject (fail-safe).
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    static final String HEADER = "X-UAI-Internal-Key";
    static final Set<String> PROTECTED = Set.of("/api/v1/auth/introspect", "/api/v1/auth/revoke");

    private final String configuredKey;

    public ApiKeyAuthFilter(String configuredKey) {
        this.configuredKey = configuredKey == null ? "" : configuredKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (PROTECTED.contains(request.getRequestURI())) {
            String provided = request.getHeader(HEADER);
            if (configuredKey.isBlank() || provided == null
                    || !MessageDigest.isEqual(
                            configuredKey.getBytes(StandardCharsets.UTF_8),
                            provided.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"unauthorized\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
```
- [ ] Step: Implement `SecurityConfig`.
```java
// src/main/java/com/uai/auth/adapter/in/web/security/SecurityConfig.java
package com.uai.auth.adapter.in.web.security;

import com.uai.auth.domain.port.out.TokenBlacklist;
import com.uai.auth.domain.port.out.TokenVerifier;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless security. Public: login/refresh, JWKS, actuator health, and introspect (gated by
 * {@link ApiKeyAuthFilter}). Bearer-authenticated: logout, me. ADMIN + internal key: revoke.
 */
@Configuration
public class SecurityConfig {

    @Value("${uai.auth.internal.api-key:}")
    private String internalApiKey;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenVerifier verifier, TokenBlacklist blacklist)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/.well-known/jwks").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        // gated by ApiKeyAuthFilter (X-UAI-Internal-Key):
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/introspect").permitAll()
                        // internal key (filter) + ADMIN bearer:
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/revoke").hasRole("ADMIN")
                        .requestMatchers("/api/v1/auth/logout", "/api/v1/auth/me").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(unauthorizedEntryPoint())
                        .accessDeniedHandler(forbiddenHandler()))
                .addFilterBefore(new ApiKeyAuthFilter(internalApiKey),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new JwtAuthenticationFilter(verifier, blacklist),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, ex) -> writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "unauthorized");
    }

    private static AccessDeniedHandler forbiddenHandler() {
        return (request, response, ex) -> writeJson(response, HttpServletResponse.SC_FORBIDDEN, "forbidden");
    }

    private static void writeJson(HttpServletResponse response, int status, String error) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + error + "\"}");
    }
}
```
- [ ] Step: Verify it compiles — `mvn -q -DskipTests compile` → `BUILD SUCCESS`.
- [ ] Step: Commit — `git add -A && git commit -m "feat(auth): stateless Spring Security (JWT filter + internal-key filter + authz map)"`

---

### Task 13: Full integration test (login → introspect → logout/revoke → refresh)

**Files:** Create `src/test/java/com/uai/auth/adapter/in/web/AuthFlowIT.java`.
**Test:** itself (the canonical end-to-end flow with Testcontainers PG + Redis).

- [ ] Step: Write the failing integration test.
```java
// src/test/java/com/uai/auth/adapter/in/web/AuthFlowIT.java
package com.uai.auth.adapter.in.web;

import com.uai.auth.domain.model.AccessClaims;
import com.uai.auth.domain.port.out.TokenVerifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Canonical end-to-end flow against Testcontainers Postgres + Redis (Flyway V1+V2 applied):
 * login → introspect(active:true) → logout → introspect(active:false); refresh rotation;
 * revoke by jti; JWKS; and the internal-key + bad-credentials guards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthFlowIT {

    private static final String INTERNAL_KEY = "test-internal-key";
    private static final String TENANT_ID = "00000000-0000-0000-0000-000000000001"; // seeded by V2
    private static final String IT_EMAIL = "it-admin@uai.test";
    private static final String IT_PASSWORD = "it-secret-123";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired TokenVerifier verifier;          // to extract jti for the revoke test
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void seedItAdmin() {
        jdbc.update("DELETE FROM user_account WHERE email = ?", IT_EMAIL);
        jdbc.update("""
                INSERT INTO user_account (id, tenant_id, email, password_hash, role, enabled, created_at)
                VALUES (gen_random_uuid(), ?::uuid, ?, ?, 'ADMIN', true, now())
                """, TENANT_ID, IT_EMAIL, encoder.encode(IT_PASSWORD));
    }

    private JsonNode login() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + IT_EMAIL + "\",\"password\":\"" + IT_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").exists())
                .andExpect(jsonPath("$.refresh_token").exists())
                .andExpect(jsonPath("$.expires_in").value(900))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    private void introspect(String token, boolean expectedActive) throws Exception {
        mvc.perform(post("/api/v1/auth/introspect")
                        .header("X-UAI-Internal-Key", INTERNAL_KEY)
                        .contentType("application/json")
                        .content("{\"token\":\"" + token + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(expectedActive));
    }

    @Test
    void loginIntrospectLogoutIntrospect() throws Exception {
        String access = login().get("access_token").asText();

        mvc.perform(post("/api/v1/auth/introspect")
                        .header("X-UAI-Internal-Key", INTERNAL_KEY)
                        .contentType("application/json")
                        .content("{\"token\":\"" + access + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.email").value(IT_EMAIL))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.tenant_id").value(TENANT_ID));

        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + access))
                .andExpect(status().isNoContent());

        introspect(access, false);  // blacklisted via logout
    }

    @Test
    void introspectWithoutInternalKeyIs401() throws Exception {
        String access = login().get("access_token").asText();
        mvc.perform(post("/api/v1/auth/introspect")
                        .contentType("application/json")
                        .content("{\"token\":\"" + access + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsTheProfile() throws Exception {
        String access = login().get("access_token").asText();
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(IT_EMAIL))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void badCredentialsIs401() throws Exception {
        mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"email\":\"" + IT_EMAIL + "\",\"password\":\"WRONG\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_credentials"));
    }

    @Test
    void refreshRotatesAndOldRefreshIsRejected() throws Exception {
        String oldRefresh = login().get("refresh_token").asText();

        String body = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refresh_token\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(body).get("refresh_token").asText()).isNotEqualTo(oldRefresh);

        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("{\"refresh_token\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokeByJtiNeedsKeyPlusAdminAndThenIntrospectsInactive() throws Exception {
        String access = login().get("access_token").asText();
        AccessClaims claims = verifier.verify(access);

        // missing internal key → 401 (ApiKeyAuthFilter)
        mvc.perform(post("/api/v1/auth/revoke")
                        .header("Authorization", "Bearer " + access)
                        .contentType("application/json")
                        .content("{\"jti\":\"" + claims.jti() + "\"}"))
                .andExpect(status().isUnauthorized());

        // key + ADMIN bearer → 204
        mvc.perform(post("/api/v1/auth/revoke")
                        .header("X-UAI-Internal-Key", INTERNAL_KEY)
                        .header("Authorization", "Bearer " + access)
                        .contentType("application/json")
                        .content("{\"jti\":\"" + claims.jti() + "\"}"))
                .andExpect(status().isNoContent());

        introspect(access, false);
    }

    @Test
    void jwksExposesThePublicKey() throws Exception {
        mvc.perform(get("/.well-known/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].n").exists());
    }
}
```
- [ ] Step: Run it, verify it FAILS first (it must exist and the chain must be wired) — `mvn -q verify -Djacoco.skip=true -Dit.test=AuthFlowIT -Dsurefire.failIfNoSpecifiedTests=false`. If any wiring is wrong it fails here; iterate until green. Expected once Tasks 11-12 are correct: 7 tests pass.
- [ ] Step: Run tests, verify PASS — same command → `BUILD SUCCESS`, 7 tests pass.
- [ ] Step: Commit — `git add -A && git commit -m "test(auth): full Testcontainers flow (login/introspect/logout/refresh/revoke/jwks)"`

---

### Task 14: Full build + coverage gate green

**Files:** none (verification task). If JaCoCo <80%, add the gated generator test (Task 15) and minor tests; the suite already exercises every package.
**Test:** the whole suite.

- [ ] Step: Run the full gated build — `mvn -q verify` → Surefire (unit) + Failsafe (3 ITs) + JaCoCo `check` ≥80% line, all green. Expected `BUILD SUCCESS`.
- [ ] Step: If JaCoCo fails the 80% gate, inspect `target/site/jacoco/index.html`, add focused unit tests for any uncovered branch (e.g. `RsaKeyProvider.parsePublic` failure path), re-run.
- [ ] Step: Commit (only if tests were added) — `git add -A && git commit -m "test(auth): close coverage gaps to satisfy JaCoCo 80% gate"`

---

### Task 15: Seed-hash generator (gated test)

**Files:** Create `src/test/java/com/uai/auth/tools/BcryptHashGeneratorTest.java`.
**Test:** itself (gated: skipped in CI, run by the user with `RAW_PASSWORD`).

- [ ] Step: Write the generator (it is a *tool*, gated by an env var so CI skips it).
```java
// src/test/java/com/uai/auth/tools/BcryptHashGeneratorTest.java
package com.uai.auth.tools;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * One-off seed-hash generator (NOT a real test). Skipped unless RAW_PASSWORD is set, so CI stays
 * green. To generate a prod seed hash, run locally with the OUT-OF-BAND password:
 *
 *   RAW_PASSWORD='<the password>' mvn -q -Dtest=BcryptHashGeneratorTest test
 *
 * Copy the printed BCRYPT12_HASH=... value into V2__seed_tenant_and_admins.sql
 * (replacing __BCRYPT12_HASH_MARLEY__ / __BCRYPT12_HASH_VIVIAN__). NEVER commit the plaintext.
 */
class BcryptHashGeneratorTest {

    @Test
    void printBcrypt12Hash() {
        String raw = System.getenv("RAW_PASSWORD");
        Assumptions.assumeTrue(raw != null && !raw.isBlank(),
                "set RAW_PASSWORD to generate a bcrypt(12) hash");
        System.out.println("BCRYPT12_HASH=" + new BCryptPasswordEncoder(12).encode(raw));
    }
}
```
- [ ] Step: Verify it is skipped (no env) — `mvn -q -Dtest=BcryptHashGeneratorTest test` → `Tests run: 1, Skipped: 1` (assumption aborted), build green.
- [ ] Step: Commit — `git add -A && git commit -m "chore(auth): gated bcrypt(12) seed-hash generator (CI-skipped)"`

---

### Task 16: Dockerfile + CI workflow + README

**Files:** Create `Dockerfile`, `.github/workflows/deploy.yml`, `README.md`.
**Test:** local Docker build (the executor runs it to confirm the image builds; CI runs `mvn verify` then build-push).

- [ ] Step: Create the `Dockerfile` (port 8084).
```dockerfile
# ── Stage 1: build ──────────────────────────────────────────────────────────────
# Tests are NOT run here — they are gated in the CI `test` job before this stage.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -Dmaven.test.skip=true -q && \
    cp target/*.jar target/app.jar

# ── Stage 2: runtime ────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/target/app.jar app.jar

# All runtime config comes from container env — no secrets baked in.
# Required: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, REDIS_HOST, REDIS_PORT,
#           UAI_AUTH_JWT_PRIVATE_KEY, UAI_AUTH_JWT_PUBLIC_KEY, UAI_INTERNAL_API_KEY,
#           SPRING_PROFILES_ACTIVE=prod
EXPOSE 8084
ENTRYPOINT ["java", \
            "-XX:MaxRAMPercentage=75.0", \
            "-XX:+UseContainerSupport", \
            "-jar", "app.jar"]
```
- [ ] Step: Create the CI workflow (identical structure to the sibling template — image name derives from the repo, so no edits needed).
```yaml
# .github/workflows/deploy.yml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE: ghcr.io/${{ github.repository }}

jobs:
  test:
    name: Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: temurin
          cache: maven
      - name: Backend tests (mvn verify)
        run: mvn verify -B

  build-push:
    name: Build & Push
    needs: test
    runs-on: ubuntu-latest
    if: github.event_name == 'push' && github.ref == 'refs/heads/main'
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4
      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}
      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3
      - name: Build and push image
        uses: docker/build-push-action@v5
        with:
          context: .
          file: Dockerfile
          push: true
          tags: |
            ${{ env.IMAGE }}:latest
            ${{ env.IMAGE }}:${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
```
- [ ] Step: Create `README.md`.
```markdown
# uai-auth

uAI dedicated authentication service (`:8084`). Issues RS256 JWTs, validates its own Bearer
tokens, exposes an internal introspection/revoke API (`X-UAI-Internal-Key`) and a JWKS endpoint.
Hexagonal, Java 21 / Spring Boot 3.3.6, Postgres (`uai_auth`, Flyway on startup) + Redis blacklist.

## Endpoints

| Method | Path | Auth | Result |
|---|---|---|---|
| POST | `/api/v1/auth/login` | public | `{access_token, refresh_token, expires_in}` / 401 |
| POST | `/api/v1/auth/refresh` | public | rotated tokens (old refresh revoked) / 401 |
| POST | `/api/v1/auth/logout` | Bearer | 204 (access jti → blacklist) |
| GET | `/api/v1/auth/me` | Bearer | `{user_id, email, role, tenant_id}` |
| GET | `/.well-known/jwks` | public | JWKS public key |
| POST | `/api/v1/auth/introspect` | `X-UAI-Internal-Key` | `{active, user_id, email, tenant_id, role, expires_at}` / `{active:false}` / 401 |
| POST | `/api/v1/auth/revoke` | `X-UAI-Internal-Key` + ADMIN Bearer | 204 |

Access TTL 15min, refresh TTL 7d (rotated). Roles: ADMIN, AGENCY_MANAGER, CLIENT_VIEWER, SELF_SERVICE_USER.

## Run / test

    mvn verify          # unit + Testcontainers (PG+Redis) + JaCoCo 80% gate

Local run needs Postgres `uai_auth` + Redis (see `uai-infra`). Env: `DB_*`, `REDIS_*`,
`UAI_AUTH_JWT_PRIVATE_KEY`, `UAI_AUTH_JWT_PUBLIC_KEY`, `UAI_INTERNAL_API_KEY`,
`SPRING_PROFILES_ACTIVE=prod`. With the key vars blank an ephemeral keypair is generated (DEV ONLY).

## Generating the RS256 keypair

    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
    openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem

Put `jwt-private.pem` in GitHub Secret `UAI_AUTH_JWT_PRIVATE_KEY` and `jwt-public.pem` in
`UAI_AUTH_JWT_PUBLIC_KEY` (wired into the container env by `uai-infra`). Never commit the private key.

## Generating the seed hashes

`V2__seed_tenant_and_admins.sql` ships with placeholders. Generate each hash out-of-band:

    RAW_PASSWORD='<password>' mvn -q -Dtest=BcryptHashGeneratorTest test

Copy the printed `BCRYPT12_HASH=...` into the migration (replace `__BCRYPT12_HASH_MARLEY__` /
`__BCRYPT12_HASH_VIVIAN__`). Plaintext never enters the repo.
```
- [ ] Step: Verify the image builds — `docker build -t uai-auth:local .` → `Successfully built` / `naming to docker.io/library/uai-auth:local done`.
- [ ] Step: Commit — `git add -A && git commit -m "build(auth): Dockerfile (:8084) + GHCR CI workflow + README"`

---

### Task 17: USER step — repo creation, secrets, seed hashes (classifier-blocked for the agent)

**Files:** none in this repo (actions the human performs).
**Test:** GitHub Actions `CI` goes green and publishes `ghcr.io/mddinizbh/uai-auth:latest`.

- [ ] Step (USER): Create the GitHub repo and push (the agent is blocked from `gh repo create` + bulk push — scaffold-from-template convention):
```bash
cd /Users/marleydiniz/IdeaProjects/personal/uai/uai-auth
gh repo create mddinizbh/uai-auth --private --source . --remote origin
git branch -M main
git push -u origin main
```
- [ ] Step (USER): Generate the RS256 keypair and add the two GitHub Secrets (private = PKCS8 PEM, public = X.509 PEM):
```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/jwt-private.pem
openssl rsa -in /tmp/jwt-private.pem -pubout -out /tmp/jwt-public.pem
gh secret set UAI_AUTH_JWT_PRIVATE_KEY --repo mddinizbh/uai-auth < /tmp/jwt-private.pem
gh secret set UAI_AUTH_JWT_PUBLIC_KEY  --repo mddinizbh/uai-auth < /tmp/jwt-public.pem
rm -f /tmp/jwt-private.pem /tmp/jwt-public.pem
```
> `UAI_INTERNAL_API_KEY` already exists in uai-infra; the keypair env wiring into the container is done in the uai-infra plan.
- [ ] Step (USER): Generate the two prod seed hashes with the out-of-band passwords and paste them into the migration:
```bash
RAW_PASSWORD='<marley password>' mvn -q -Dtest=BcryptHashGeneratorTest test   # → BCRYPT12_HASH=...
RAW_PASSWORD='<vivian password>' mvn -q -Dtest=BcryptHashGeneratorTest test   # → BCRYPT12_HASH=...
# Edit src/main/resources/db/migration/V2__seed_tenant_and_admins.sql:
#   __BCRYPT12_HASH_MARLEY__ → marley's hash,  __BCRYPT12_HASH_VIVIAN__ → vivian's hash
git add src/main/resources/db/migration/V2__seed_tenant_and_admins.sql
git commit -m "chore(auth): seed prod admin bcrypt(12) hashes (out-of-band)"
git push
```
- [ ] Step (USER): Confirm the `CI` workflow is green and the image is published — `gh run watch --repo mddinizbh/uai-auth` then `gh api /user/packages/container/uai-auth/versions --jq '.[0].metadata.container.tags'` shows `latest`.
- [ ] Step: Hand off to the **uai-infra** plan (compose service `uai-auth`, nginx `/api/auth/` rewrite, `uai_auth` DB init, `deploy.yml`, keypair secrets) — out of scope for this repo.