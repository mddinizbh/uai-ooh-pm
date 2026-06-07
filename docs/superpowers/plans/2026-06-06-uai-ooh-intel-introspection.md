# uai-ooh-intel — Introspection Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (- [ ]) syntax.

**Goal:** Reconcile `uai-ooh-intel`'s real introspection client (`UaiAuthTokenIntrospector`) to the canonical `uai-auth` contract (X-UAI-Internal-Key header, JSON `{"token"}` body, parse `{active,user_id,email}` → `AuthenticatedUser(user_id,email)`) and flip the **prod** profile to `introspection`, while keeping **STUB** the default for dev/test/CI.

**Architecture:** Auth-only resource server (ADR-004 — no tenant/RLS). `BearerTokenAuthenticationFilter` delegates to a `TokenIntrospector` chosen by `ooh.intel.auth.mode`: `STUB` (default, dev/test/CI) or `INTROSPECTION` (prod). `CachingTokenIntrospector` wraps the chosen delegate with a short token-hash cache. This change rewrites only the `INTROSPECTION` delegate + the prod config; the stub path and its `iss=uai-auth-stub` acceptance stay untouched and are simply not wired in prod.

**Tech Stack:** Java 21, Spring Boot 3.3.6 (+ Security), Maven, `RestClient`, Jackson, JUnit 5 + AssertJ, Spring `MockRestServiceServer` (already on the classpath via `spring-boot-starter-test`; no new dependency), Testcontainers for the existing ITs.

---

## File Structure

| File | Create/Modify | Single responsibility |
|---|---|---|
| `src/main/java/com/uai/ooh/intel/adapter/out/auth/UaiAuthTokenIntrospector.java` | **Modify** (constructor L33–47, `introspect` L49–77, response record L75–77, imports L7–9) | The 3 deltas: send `X-UAI-Internal-Key` (drop HTTP Basic), POST JSON `{"token"}` (drop form-urlencoded), parse `{active,user_id,email}` → `AuthenticatedUser(user_id,email)`; fail-closed on any `RestClientException`. |
| `src/main/java/com/uai/ooh/intel/adapter/in/web/security/AuthProperties.java` | **Modify** (default L33–35, record L45) | Narrow `Introspection` record from `(url, clientId, clientSecret)` to `(url, internalKey)`. |
| `src/main/java/com/uai/ooh/intel/adapter/in/web/security/SecurityConfig.java` | **Modify** (L77–82) | Wire the `INTROSPECTION` branch to the new 3-arg constructor passing `internalKey`. |
| `src/main/resources/application.yml` | **Modify** (L60–63) | Replace `introspection.{client-id,client-secret}` with `introspection.internal-key`. |
| `src/main/resources/application-prod.yml` | **Modify** (L15–22) | Flip prod to `mode=introspection` + canonical `introspection.url` + `internal-key=${UAI_INTERNAL_API_KEY}` (stub no longer wired in prod). |
| `src/test/java/com/uai/ooh/intel/adapter/out/auth/UaiAuthTokenIntrospectorTest.java` | **Create** | Drives the 3 deltas + fail-closed against a fake uai-auth (`MockRestServiceServer`). |
| `src/test/java/com/uai/ooh/intel/adapter/in/web/security/ProdAuthProfileBindingTest.java` | **Create** | Proves the real `application-prod.yml` binds to `mode=INTROSPECTION`, canonical url, and `internal-key` ← `UAI_INTERNAL_API_KEY` (stub/`iss=uai-auth-stub` no longer wired in prod). |

**Build/run notes (grounded in this repo):**
- Maven on PATH defaults to **Java 26** (Homebrew); JaCoCo 0.8.12 (pinned in `pom.xml`) **breaks on Java >21**. Always run Maven with Java 21: prefix every `mvn` with `JAVA_HOME=$(/usr/libexec/java_home -v 21)` (resolves to `/Users/marleydiniz/Library/Java/JavaVirtualMachines/ms-21.0.10/Contents/Home`).
- Unit tests (`*Test.java`) run under Surefire (`mvn test`). Integration tests (`*IT.java`, Testcontainers) run under Failsafe in `mvn verify` (needs Docker; `api.version=1.44` override already in `pom.xml`).
- CI workflow is `.github/workflows/deploy.yml` (`name: CI`); the `test` job runs `mvn verify -B` and gates the GHCR image build. This repo never deploys (infra does).

---

### Task 1: Reconcile `UaiAuthTokenIntrospector` to the canonical `/introspect` contract

This is one atomic compile unit: the introspector's new 3-arg constructor, the narrowed `AuthProperties.Introspection` record, the `SecurityConfig` wiring, and the base `application.yml` key must change together (Java won't compile otherwise). The new `UaiAuthTokenIntrospectorTest` is the red→green driver.

**Files:**
- Create: `src/test/java/com/uai/ooh/intel/adapter/out/auth/UaiAuthTokenIntrospectorTest.java`
- Modify: `src/main/java/com/uai/ooh/intel/adapter/out/auth/UaiAuthTokenIntrospector.java` (full rewrite of constructor L33–47 + `introspect` L49–77 + response record L75–77; drop imports `LinkedMultiValueMap`/`MultiValueMap` L8–9)
- Modify: `src/main/java/com/uai/ooh/intel/adapter/in/web/security/AuthProperties.java` (default L33–35, record L45)
- Modify: `src/main/java/com/uai/ooh/intel/adapter/in/web/security/SecurityConfig.java` (L77–82)
- Modify: `src/main/resources/application.yml` (L60–63)

**Test:** `src/test/java/com/uai/ooh/intel/adapter/out/auth/UaiAuthTokenIntrospectorTest.java`

- [ ] Step: Create the feature branch from `main` (current branch is `fix/cors-prod-origin`; do not build on it).
  ```bash
  cd /Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-intel
  git checkout main && git pull --ff-only
  git checkout -b feat/auth-introspection-cutover
  ```

- [ ] Step: Write the failing test. Create `src/test/java/com/uai/ooh/intel/adapter/out/auth/UaiAuthTokenIntrospectorTest.java`. It binds a `MockRestServiceServer` to the `RestClient.Builder` the introspector receives, asserting the header, JSON body, and parse, plus fail-closed on 401/5xx/inactive, plus null/blank short-circuit and the blank-url guard. It references the **new** 3-arg constructor `new UaiAuthTokenIntrospector(builder, url, internalKey)`.
  ```java
  package com.uai.ooh.intel.adapter.out.auth;

  import com.uai.ooh.intel.domain.model.AuthenticatedUser;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.springframework.http.HttpHeaders;
  import org.springframework.http.HttpMethod;
  import org.springframework.http.HttpStatus;
  import org.springframework.http.MediaType;
  import org.springframework.test.web.client.MockRestServiceServer;
  import org.springframework.web.client.RestClient;

  import java.util.Optional;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
  import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
  import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
  import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
  import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
  import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
  import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
  import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
  import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

  /**
   * Contract test of the real uai-auth introspection client against a fake uai-auth
   * (Spring MockRestServiceServer bound to the RestClient.Builder). Locks the three deltas
   * (X-UAI-Internal-Key header, JSON {"token"} body, parse user_id/email) and fail-closed
   * behaviour (inactive / 401 / 5xx -> empty; never an exception to the caller).
   */
  class UaiAuthTokenIntrospectorTest {

      private static final String URL = "http://uai-auth:8084/api/v1/auth/introspect";
      private static final String KEY = "internal-key-abc";

      private RestClient.Builder builder;
      private MockRestServiceServer server;
      private UaiAuthTokenIntrospector introspector;

      @BeforeEach
      void setUp() {
          builder = RestClient.builder();
          server = MockRestServiceServer.bindTo(builder).build();
          introspector = new UaiAuthTokenIntrospector(builder, URL, KEY);
      }

      @Test
      void sendsInternalKeyHeaderAndJsonBodyAndParsesActiveUser() {
          server.expect(requestTo(URL))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(header("X-UAI-Internal-Key", KEY))
                  .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION)) // delta #1: no HTTP Basic
                  .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                  .andExpect(content().json("{\"token\":\"jwt-123\"}")) // delta #2: JSON, not form
                  .andRespond(withSuccess(
                          "{\"active\":true,\"user_id\":\"u-42\",\"email\":\"marley.diniz@gmail.com\","
                                  + "\"tenant_id\":\"t-1\",\"role\":\"ADMIN\",\"expires_at\":\"2026-06-06T12:00:00Z\"}",
                          MediaType.APPLICATION_JSON));

          Optional<AuthenticatedUser> user = introspector.introspect("jwt-123");

          server.verify();
          assertThat(user).isPresent();
          assertThat(user.get().subject()).isEqualTo("u-42");           // delta #3: user_id -> subject
          assertThat(user.get().email()).isEqualTo("marley.diniz@gmail.com");
      }

      @Test
      void inactiveResponseIsRejected() {
          server.expect(requestTo(URL))
                  .andRespond(withSuccess("{\"active\":false}", MediaType.APPLICATION_JSON));

          assertThat(introspector.introspect("revoked-jwt")).isEmpty();
          server.verify();
      }

      @Test
      void unauthorizedFromAuthServerFailsClosed() {
          // bad/missing internal key at uai-auth -> 401; must surface as "not authenticated", not a 500
          server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

          assertThat(introspector.introspect("jwt-123")).isEmpty();
          server.verify();
      }

      @Test
      void serverErrorFailsClosed() {
          server.expect(requestTo(URL)).andRespond(withServerError());

          assertThat(introspector.introspect("jwt-123")).isEmpty();
          server.verify();
      }

      @Test
      void nullOrBlankTokenShortCircuitsWithoutCallingAuth() {
          assertThat(introspector.introspect(null)).isEmpty();
          assertThat(introspector.introspect("   ")).isEmpty();
          // no server expectations set -> proves no HTTP call was made
      }

      @Test
      void blankUrlIsRejectedAtConstruction() {
          assertThatIllegalStateException()
                  .isThrownBy(() -> new UaiAuthTokenIntrospector(RestClient.builder(), "  ", KEY));
      }
  }
  ```

- [ ] Step: Run it, verify it FAILS (compile error — the new 3-arg constructor does not exist yet).
  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -B -Dtest=UaiAuthTokenIntrospectorTest test
  ```
  Expected: `BUILD FAILURE` at `maven-compiler-plugin ... testCompile` with `constructor UaiAuthTokenIntrospector ... cannot be applied to given types; required: RestClient.Builder,String,String,String found: RestClient.Builder,String,String`.

- [ ] Step: Minimal implementation — rewrite `UaiAuthTokenIntrospector.java` to the new constructor + header + JSON body + `user_id` parse. Replace the whole file with:
  ```java
  package com.uai.ooh.intel.adapter.out.auth;

  import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
  import com.fasterxml.jackson.annotation.JsonProperty;
  import com.uai.ooh.intel.domain.model.AuthenticatedUser;
  import com.uai.ooh.intel.domain.port.out.TokenIntrospector;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.http.MediaType;
  import org.springframework.web.client.RestClient;
  import org.springframework.web.client.RestClientException;

  import java.util.Optional;

  /**
   * Real introspection against {@code uai-auth}: POSTs the access token to
   * {@code /api/v1/auth/introspect} authenticated with the shared internal key
   * ({@code X-UAI-Internal-Key}, ADR-040) and trusts the {@code active} flag (revocation is
   * respected via the uai-auth Redis blacklist).
   *
   * <p>Selected only when {@code ooh.intel.auth.mode=introspection} (prod). The default mode is
   * {@code stub} ({@link StubTokenIntrospector}) for dev/test/CI.
   *
   * <p>Failures (network, 4xx/5xx, malformed body) are treated as "not authenticated" (empty) —
   * <b>fail closed</b> — and logged at WARN; they never leak as 500s to the caller.
   *
   * <p>Auth-only (ADR-004): {@code intel} consumes {@code user_id} + {@code email} and ignores
   * {@code tenant_id}/{@code role}.
   */
  public final class UaiAuthTokenIntrospector implements TokenIntrospector {

      /** Internal service-to-service auth header (ADR-040). */
      static final String INTERNAL_KEY_HEADER = "X-UAI-Internal-Key";

      private static final Logger log = LoggerFactory.getLogger(UaiAuthTokenIntrospector.class);

      private final RestClient restClient;

      public UaiAuthTokenIntrospector(RestClient.Builder builder,
                                      String introspectionUrl,
                                      String internalKey) {
          if (introspectionUrl == null || introspectionUrl.isBlank()) {
              throw new IllegalStateException(
                      "ooh.intel.auth.introspection.url must be set when ooh.intel.auth.mode=introspection");
          }
          RestClient.Builder configured = builder.baseUrl(introspectionUrl);
          if (internalKey != null && !internalKey.isBlank()) {
              configured = configured.defaultHeader(INTERNAL_KEY_HEADER, internalKey);
          }
          this.restClient = configured.build();
      }

      @Override
      public Optional<AuthenticatedUser> introspect(String token) {
          if (token == null || token.isBlank()) {
              return Optional.empty();
          }
          try {
              IntrospectionResponse response = restClient.post()
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .body(new IntrospectionRequest(token))
                      .retrieve()
                      .body(IntrospectionResponse.class);

              if (response != null && response.active()) {
                  String subject = response.userId() != null ? response.userId() : "unknown";
                  return Optional.of(new AuthenticatedUser(subject, response.email()));
              }
          } catch (RestClientException e) {
              log.warn("uai-auth introspection call failed, treating token as invalid: {}", e.getMessage());
          }
          return Optional.empty();
      }

      /** Request body for {@code POST /api/v1/auth/introspect}: {@code {"token":"<jwt>"}}. */
      private record IntrospectionRequest(String token) {
      }

      /** Subset of the uai-auth introspection response that {@code intel} consumes. */
      @JsonIgnoreProperties(ignoreUnknown = true)
      private record IntrospectionResponse(
              boolean active,
              @JsonProperty("user_id") String userId,
              String email) {
      }
  }
  ```

- [ ] Step: Narrow the `AuthProperties.Introspection` record so `SecurityConfig` can pass `internalKey`. In `src/main/java/com/uai/ooh/intel/adapter/in/web/security/AuthProperties.java`, change the default at L33–35:
  ```java
          if (introspection == null) {
              introspection = new Introspection(null, null);
          }
  ```
  and the record at L45:
  ```java
      /** {@code uai-auth} introspection endpoint coordinates (INTROSPECTION mode only). */
      public record Introspection(String url, String internalKey) {
      }
  ```

- [ ] Step: Wire the `INTROSPECTION` branch in `SecurityConfig.java` to the new constructor. Replace L77–82:
  ```java
              case INTROSPECTION -> new UaiAuthTokenIntrospector(
                      restClientBuilder.getIfAvailable(RestClient::builder),
                      props.introspection().url(),
                      props.introspection().internalKey());
  ```

- [ ] Step: Update the base `application.yml` introspection block (L60–63) — drop `client-id`/`client-secret`, add `internal-key`:
  ```yaml
        introspection:
          url: ${OOH_INTEL_AUTH_INTROSPECTION_URL:}
          # X-UAI-Internal-Key sent to uai-auth (ADR-040). Unused in STUB mode; required in prod.
          internal-key: ${UAI_INTERNAL_API_KEY:}
  ```

- [ ] Step: Run the new test, verify PASS.
  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -B -Dtest=UaiAuthTokenIntrospectorTest test
  ```
  Expected: `BUILD SUCCESS`, `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] Step: Run the full unit suite to prove the stub path and the bearer filter stay green (no regression from the record/wiring change).
  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -B test
  ```
  Expected: `BUILD SUCCESS`; `StubTokenIntrospectorTest`, `CachingTokenIntrospectorTest`, `BearerTokenAuthenticationFilterTest` all pass (the stub still accepts the literal token and `iss=uai-auth-stub` JWT — unchanged).

- [ ] Step: Commit.
  ```bash
  git add src/main/java/com/uai/ooh/intel/adapter/out/auth/UaiAuthTokenIntrospector.java \
          src/main/java/com/uai/ooh/intel/adapter/in/web/security/AuthProperties.java \
          src/main/java/com/uai/ooh/intel/adapter/in/web/security/SecurityConfig.java \
          src/main/resources/application.yml \
          src/test/java/com/uai/ooh/intel/adapter/out/auth/UaiAuthTokenIntrospectorTest.java
  git commit -m "feat(auth): reconcilia introspector com contrato uai-auth (X-UAI-Internal-Key + body JSON {token} + parse user_id/email, fail-closed)"
  ```

---

### Task 2: Flip the prod profile to `introspection` (retire the stub in prod)

In prod, `mode=introspection` means the `StubTokenIntrospector` (and its `iss=uai-auth-stub` acceptance) is never wired — so the stub is retired in prod with **zero** code change to the stub (dev/test/CI keep it). The `ProdAuthProfileBindingTest` loads the real `application-prod.yml` and proves prod resolves to `INTROSPECTION` + canonical url + `internal-key` ← `UAI_INTERNAL_API_KEY`.

**Files:**
- Create: `src/test/java/com/uai/ooh/intel/adapter/in/web/security/ProdAuthProfileBindingTest.java`
- Modify: `src/main/resources/application-prod.yml` (replace the auth comment block, L15–22)

**Test:** `src/test/java/com/uai/ooh/intel/adapter/in/web/security/ProdAuthProfileBindingTest.java`

- [ ] Step: Write the failing test. Create `ProdAuthProfileBindingTest.java` (same package as `AuthProperties`, so no import for it). It loads `application-prod.yml` via `YamlPropertySourceLoader`, resolves placeholders with a `UAI_INTERNAL_API_KEY` env source, and binds `ooh.intel.auth` to `AuthProperties`.
  ```java
  package com.uai.ooh.intel.adapter.in.web.security;

  import com.uai.ooh.intel.adapter.in.web.security.AuthProperties.AuthMode;
  import org.junit.jupiter.api.Test;
  import org.springframework.boot.context.properties.bind.Binder;
  import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
  import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
  import org.springframework.boot.env.YamlPropertySourceLoader;
  import org.springframework.core.env.MapPropertySource;
  import org.springframework.core.env.MutablePropertySources;
  import org.springframework.core.io.ClassPathResource;

  import java.util.Map;

  import static org.assertj.core.api.Assertions.assertThat;

  /**
   * Binds the REAL src/main/resources/application-prod.yml and asserts the prod cutover wiring:
   * mode=INTROSPECTION (so the STUB + its iss=uai-auth-stub acceptance are NOT wired in prod),
   * the canonical introspection url, and internal-key resolved from UAI_INTERNAL_API_KEY (ADR-040).
   */
  class ProdAuthProfileBindingTest {

      @Test
      void prodProfileBindsIntrospectionWithInternalKey() throws Exception {
          MutablePropertySources sources = new MutablePropertySources();
          // Simulate the env var uai-infra injects, so ${UAI_INTERNAL_API_KEY} resolves.
          sources.addFirst(new MapPropertySource("env",
                  Map.of("UAI_INTERNAL_API_KEY", "resolved-internal-key")));
          new YamlPropertySourceLoader()
                  .load("application-prod", new ClassPathResource("application-prod.yml"))
                  .forEach(sources::addLast);

          Binder binder = new Binder(
                  ConfigurationPropertySources.from(sources),
                  new PropertySourcesPlaceholdersResolver(sources));
          AuthProperties props = binder.bind("ooh.intel.auth", AuthProperties.class).get();

          assertThat(props.mode()).isEqualTo(AuthMode.INTROSPECTION);
          assertThat(props.introspection().url())
                  .isEqualTo("http://uai-auth:8084/api/v1/auth/introspect");
          assertThat(props.introspection().internalKey()).isEqualTo("resolved-internal-key");
      }
  }
  ```

- [ ] Step: Run it, verify it FAILS (the prod file still inherits `mode=stub` and has no `introspection.*`).
  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -B -Dtest=ProdAuthProfileBindingTest test
  ```
  Expected: `BUILD FAILURE`, one failed test — AssertJ `expected: INTROSPECTION but was: STUB` (and url/internalKey null).

- [ ] Step: Minimal implementation — replace the auth comment block in `application-prod.yml` (L15–22) with the real prod config:
  ```yaml
  # Auth — CUTOVER (2026-06-06): uai-auth is GREEN, so prod validates every Bearer token by
  # introspection against uai-auth. The STUB (and its iss=uai-auth-stub acceptance) stays ONLY in
  # the default/dev profile and is NOT wired here. internal-key is the shared service key (ADR-040),
  # injected by uai-infra as UAI_INTERNAL_API_KEY (startup fails fast if absent).
  ooh:
    intel:
      auth:
        mode: introspection
        introspection:
          url: ${OOH_INTEL_AUTH_INTROSPECTION_URL:http://uai-auth:8084/api/v1/auth/introspect}
          internal-key: ${UAI_INTERNAL_API_KEY}
  ```

- [ ] Step: Run the test, verify PASS.
  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -B -Dtest=ProdAuthProfileBindingTest test
  ```
  Expected: `BUILD SUCCESS`, `Tests run: 1, Failures: 0`.

- [ ] Step: Commit.
  ```bash
  git add src/main/resources/application-prod.yml \
          src/test/java/com/uai/ooh/intel/adapter/in/web/security/ProdAuthProfileBindingTest.java
  git commit -m "feat(auth): perfil prod valida por introspection contra uai-auth (stub e iss=uai-auth-stub só no dev)"
  ```

---

### Task 3: Full regression gate + open the PR

Run the exact CI gate locally (`mvn verify`), confirm the stub-mode integration tests (`IntelEndpointsIT`, `NetworkApiIT`) and the JaCoCo 80% line gate still pass, then open the PR. The PR is **not** deployed until uai-auth is up (cross-repo cutover order: uai-auth → infra → deploy uai-auth → this intel PR → portal).

**Files:** none changed (verification + PR only).

**Test:** the whole suite — `mvn verify` (Surefire unit + Failsafe `*IT` + JaCoCo check).

- [ ] Step: Confirm Docker is up (the ITs use Testcontainers Postgres).
  ```bash
  docker info >/dev/null 2>&1 && echo "docker OK" || echo "START DOCKER DESKTOP FIRST"
  ```
  Expected: `docker OK`.

- [ ] Step: Run the full gate exactly as CI does.
  ```bash
  JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -B verify
  ```
  Expected: `BUILD SUCCESS`. The stub-mode ITs (`IntelEndpointsIT` — `pingReturnsOkWithValidToken`, `apiRejectsInvalidToken`, etc.) stay green because the default/test profile is still `stub` (`application-test.yml` unchanged), and JaCoCo `check` passes (the new `UaiAuthTokenIntrospectorTest` raises coverage of the previously-untested `UaiAuthTokenIntrospector`).

- [ ] Step: Push the branch.
  ```bash
  git push -u origin feat/auth-introspection-cutover
  ```

- [ ] Step: Open the PR (cite the design doc and the cross-repo cutover order).
  ```bash
  gh pr create --base main --head feat/auth-introspection-cutover \
    --title "feat(auth): cutover intel para introspection (uai-auth)" \
    --body "$(cat <<'EOF'
  Reconcile o `UaiAuthTokenIntrospector` ao contrato canônico do `uai-auth` e liga o perfil **prod** em `mode=introspection`. STUB segue o default em dev/test/CI.

  ## 3 deltas no introspector
  1. Request auth: HTTP Basic -> header `X-UAI-Internal-Key` (ADR-040).
  2. Body: form-urlencoded `token=` -> JSON `{"token":"<jwt>"}`.
  3. Parse: `{active,user_id,email,...}` -> `AuthenticatedUser(subject=user_id, email)` (ignora tenant_id/role, ADR-004).

  ## Config
  - `application-prod.yml`: `mode=introspection`, `introspection.url=http://uai-auth:8084/api/v1/auth/introspect`, `internal-key=${UAI_INTERNAL_API_KEY}`.
  - STUB (e a aceitação de `iss=uai-auth-stub`) fica SÓ no perfil default/dev — não wired em prod.

  ## Testes
  - `UaiAuthTokenIntrospectorTest` (MockRestServiceServer fake uai-auth): header+body+parse + fail-closed (inactive/401/5xx).
  - `ProdAuthProfileBindingTest`: prova que prod resolve para INTROSPECTION + url canônica + internal-key.
  - ITs em STUB seguem verdes; JaCoCo 80% mantido.

  ## Cutover (ordem entre repos)
  uai-auth (repo+CI+GHCR) -> uai-infra (compose/nginx/db/deploy/secrets) -> deploy uai-auth (validar isolado) -> **MERGE+deploy deste PR** -> uai-portal (provider real) -> smoke e2e. NÃO mergear/deployar antes de o uai-auth estar no ar (intel passa a falhar fechado: todo /api/** -> 401).

  Design: docs/prd/2026-06-06-uai-auth-mvp-ooh-cutover-design.md (uai-ooh-pm).

  🤖 Generated with [Claude Code](https://claude.com/claude-code)
  EOF
  )"
  ```
  Expected: PR URL printed; CI `test` job runs `mvn verify -B` green on the PR.

- [ ] Step: Final verification — confirm CI is green before handing off for the deploy step.
  ```bash
  gh pr checks --watch
  ```
  Expected: the `Test` check passes. Do **not** merge until the cutover order reaches "intel PR+deploy" (uai-auth deployed and validated first).