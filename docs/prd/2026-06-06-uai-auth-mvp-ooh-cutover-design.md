# Design — uai-auth MVP + cutover do auth OOH (aposentar stubs)

> **Status:** aprovado (2026-06-06) · **Tipo:** design spec · **Driver:** aposentar os stubs de auth
> que subiram no go-live do F1 OOH, trocando-os pela autenticação real (`uai-auth`).
>
> **Fonte da verdade do serviço:** o `uai-auth` já tem spec canônica no vault — **não duplicar aqui**.
> Este doc segue o canonical e detalha só o que é específico deste esforço (reconciliações com os
> consumidores OOH já construídos + decisões tomadas em 2026-06-06).
>
> Canonical: [[adrs/adr-033-uai-auth]] · [[verticais/marketing-agency/epicos/epic-002-uai-auth-minimo|EPIC-002]] ·
> ADR-040 (internal API key) · ADR-036 (BFF cms↔auth) · ADR-004 (intel sem tenant).

## 1. Contexto & objetivo

No go-live do F1 (2026-06-06) o OOH subiu em prod com **auth stub interino**: o portal minta um JWT
falso (`iss=uai-auth-stub`) e o intel aceita por um `StubTokenIntrospector`. Funciona pra Vívian usar,
mas não é autenticação de verdade (sem usuários, sem revogação, sem senha).

**Objetivo:** subir o `uai-auth` (MVP, conforme EPIC-002) e religar os dois consumidores OOH
(`uai-ooh-intel` e `uai-portal`) pra usarem auth real, **aposentando os stubs em prod**.

## 2. Escopo

**Dentro:**
- Construir `uai-auth` MVP conforme EPIC-002 (sem desviar do canonical).
- Religar `uai-ooh-intel` → modo `INTROSPECTION` em prod.
- Religar `uai-portal` → provider real (login + refresh) no lugar do stub.
- `uai-infra`: serviço `uai-auth`, rota nginx, database `uai_auth`, deploy.

**Fora** (confirmado): self-service/`register`, role `SELF_SERVICE_USER`, recover/troca de senha, 2FA,
e religar o **uai-cms** (fica pro EPIC-003 — usará o **mesmo** padrão de introspection, ver §3.1) +
extrair o `uai-auth-commons`. Os stubs **não são apagados** — viram modo dev.

## 3. Arquitetura

```
                    ┌──────────── uaiagencia.com.br (nginx) ────────────┐
 browser (portal) ─►/             → uai-portal:3000  (SPA)
                 ├─►/api/auth/...  → uai-auth:8084   (login/refresh/logout/me)   ← NOVO
                 └─►/api/ooh/...   → uai-ooh-intel:8085 (Bearer access token)
                                              │
                  intel (mode=introspection) ─POST /introspect + X-UAI-Internal-Key─► uai-auth
                                                                                       │
                                              uai-auth ── Postgres uai_auth + Redis blacklist
```

Tudo same-origin via nginx. Revogação imediata: o intel introspecta cada token; logout joga o `jti`
na blacklist Redis → o próximo introspect retorna `active:false`.

### 3.1 Princípio: validação por introspection em TODO serviço de recurso (não é especial do cms)

O `uai-auth` é a **única fonte** de aceitação de token. **Cada serviço que recebe JWT de usuário valida
perguntando pro auth** (`POST /introspect`) — nenhum valida o JWT localmente nem conhece a estrutura
interna do token. Isso centraliza no `uai-auth` a política de "user enabled / token revogado / role mudou"
e dá revogação imediata via blacklist. (O ADR-036 introduziu isso descrevendo o cms; aqui fica claro que
é a **regra geral**, não um padrão cms-específico.)

- **`uai-ooh-intel`** (dados read-only) — **primeiro adotante** (este esforço).
- **`uai-cms`** (campanhas) — mesmo mecanismo, religado depois (EPIC-003).
- **serviços futuros** — idem.
- Serviços **sem JWT de usuário** (`uai-core`, `uai-tokenmetrics`) seguem em `X-UAI-Internal-Key` (ADR-040).

**Cliente de introspection — por-serviço agora, lib depois.** Cada serviço tem hoje seu próprio cliente
(o intel já tem `UaiAuthTokenIntrospector` + cache + filter Bearer). Extrair um `uai-auth-commons`
compartilhado (ADR-033 §Pendente) é a evolução DRY quando o cms entrar — **fora de escopo deste MVP**
(não vale refatorar o intel agora só pra isso).

## 4. uai-auth (serviço novo) — seguir EPIC-002

Sem desvio do canonical (EPIC-002 §Spec técnica): Java 21 · Spring Boot 3 + Security · Postgres `uai_auth`
· Redis · jjwt **RS256** · pacote `com.uai.auth` (hexagonal; `Role`/`TokenStatus` sealed) · porta **:8084**.

- **Endpoints públicos:** `POST /api/v1/auth/{login,refresh,logout}`, `GET /me`, `GET /.well-known/jwks`.
- **Endpoints internos** (`X-UAI-Internal-Key`, ADR-040): `POST /api/v1/auth/introspect`, `POST /revoke`.
- **Token:** access **15min** + refresh **7d** com **rotação** (old `jti` → blacklist). Blacklist Redis
  `blacklist:jti:{jti}`, TTL = resto do `exp`.
- **Schema V1 (Flyway):** `tenant`, `user_account` (bcrypt 12, `tenant_id NOT NULL`), `refresh_token`
  (ver SQL em EPIC-002 §Schema).
- **Claims do access JWT:** `sub`(user_id), `tenant_id`, `role`, `jti`, `exp`, **+ `email`**
  (adição a EPIC-002 — o portal/intel querem o email; trivial).

### Seed de produção (decisão 2026-06-06 — substitui o seed de exemplo do épico)

Tenant **uAI** + **dois usuários ADMIN** (acesso full):

| email | role |
|---|---|
| `marley.diniz@gmail.com` | `ADMIN` |
| `vivian.cristina@bhbusmidia.com.br` | `ADMIN` |

Senhas geradas fora-de-banda (2026-06-06) e seedadas **só como hash bcrypt(12)** — o texto puro
**não entra no repo**. MVP não tem reset/troca de senha; rotacionar quando o fluxo de reset existir.

## 5. Reconciliações com os consumidores OOH

### 5.1 `uai-ooh-intel` (já ~pronto: `AuthProperties` + `UaiAuthTokenIntrospector`)

- Em prod, `ooh.intel.auth.mode=introspection` (perfil `prod`); **`stub` segue o default** (dev/test/CI
  intactos). Em prod, parar de aceitar `iss=uai-auth-stub`.
- Ajustar o `UaiAuthTokenIntrospector` pro contrato canônico (3 deltas vs. o que está codado hoje):
  1. **Auth do request:** Basic auth → header **`X-UAI-Internal-Key`** (ADR-040).
  2. **Body:** form-urlencoded `token=` → **JSON `{"token":"<jwt>"}`** (EPIC-002).
  3. **Parse da resposta:** alinhar com `{active, user_id, email, tenant_id, role, expires_at}` →
     `AuthenticatedUser(subject=user_id, email)`. Intel ignora `tenant_id`/`role` (ADR-004).
- Config: `ooh.intel.auth.introspection.url=http://uai-auth:8084/api/v1/auth/introspect` +
  `ooh.intel.auth.introspection.internalKey=${UAI_INTERNAL_API_KEY}`. Cache curto (já existe:
  `CachingTokenIntrospector`).

### 5.2 `uai-portal` (trocar o provider; `AuthContext`/`tokenStore`/interceptor mudam pouco)

- Novo provider real substituindo `stubProvider`: `login(email,senha)` → `POST /api/auth/login` →
  `{access_token, refresh_token, expires_in}`. `tokenStore` passa a guardar **os 2 tokens**.
- **Refresh-on-401 transparente** no `http.ts`: no 401 do intel, dispara `POST /api/auth/refresh`
  **uma vez** (com **fila** pros requests concorrentes), refaz a request original; refresh falhou →
  `clearSession()` + redirect login. Rotação troca os 2 tokens.
- Exibição de perfil (sidebar/footer): `GET /api/auth/me` após login (não stuffar display no JWT).
  **`ADMIN` = acesso total** no front (hoje só mapeia `AGENCY_MANAGER`/`CLIENT_VIEWER`).
- `logout()` → `POST /api/auth/logout`.
- `.env.production`: `VITE_AUTH_MODE=sso` + `VITE_AUTH_BASE_URL=/api/auth`. **`stubProvider` fica** pro
  dev (toggle por `VITE_AUTH_MODE`).

### 5.3 Contrato `/introspect` (reconciliado — a fonte do back é o uai-auth)

```
POST /api/v1/auth/introspect
Headers: X-UAI-Internal-Key: <UAI_INTERNAL_API_KEY>
Body:    { "token": "<access_jwt>" }
200:     { "active": true,  "user_id": "...", "email": "...", "tenant_id": "...",
           "role": "ADMIN", "expires_at": "<iso8601>" }
         { "active": false }                      # revogado / expirado / user disabled
401:     sem/!= X-UAI-Internal-Key
```

## 6. Infra (`uai-infra`)

- `docker-compose.yml`: serviço **`uai-auth`** (`ghcr.io/${GITHUB_USER}/uai-auth:latest`, :8084, profile
  prod, `DB_*`=uai_auth, `REDIS_*`, env das chaves + internal key, `depends_on` postgres/redis healthy, ~384M).
- `nginx.conf`: `location /api/auth/ { rewrite ^/api/auth/(.*)$ /api/v1/auth/$1 break; proxy_pass http://uai-auth:8084; }`
  (strip `/api/auth` → `/api/v1/auth`).
- Init script: criar database **`uai_auth`** (hoje cria uai_cms/ooh).
- `deploy.yml`: `deploy_service uai-auth` no `deploy_apps`.
- Repo `uai-auth` (GitHub mddinizbh) + CI build-push (padrão dos outros).

## 7. Decisões de segurança (confirmadas 2026-06-06)

- **a) Keypair RS256:** gerado local (`openssl`), **private** em GitHub Secret `UAI_AUTH_JWT_PRIVATE_KEY`
  (injetada por env, nunca no repo), public via `/.well-known/jwks`. Rotação anual manual.
- **b) Internal key:** reusar o secret **`UAI_INTERNAL_API_KEY`** já existente no uai-infra; o intel
  passa a recebê-lo por env. Comparação timing-safe (`MessageDigest.isEqual`).
- **c) Seed:** 2 usuários ADMIN (§4) — hash bcrypt(12), texto puro fora do repo.
- **d) Stubs viram modo dev** — intel `mode=stub`, portal `VITE_AUTH_MODE=stub`; prod = real.

## 8. Testes

- **uai-auth:** unit (BCrypt 12 → `$2a$12$`; JwtIssuer claims/jti/exp); integração Testcontainers (PG+Redis):
  login → introspect (active) → logout/revoke → introspect (active:false); smoke HTTP (login 200, introspect).
- **intel:** IT do `UaiAuthTokenIntrospector` contra um uai-auth fake/WireMock (header+body+parse corretos;
  fail-closed em 5xx/timeout); o modo `stub` segue verde no CI.
- **portal:** testes do refresh-on-401 (fila de concorrentes, rotação, falha→logout); `me` popula o perfil;
  ADMIN = acesso total.
- **e2e prod (smoke):** `login` → Bearer no `/api/ooh/api/lines` (200) → `logout` → mesma chamada (401).

## 9. Sequência de cutover (rollout)

1. uai-auth: repo + CI + imagem no GHCR.
2. uai-infra: compose + nginx (`/api/auth/`) + init `uai_auth` + deploy.yml + secrets (keypair).
3. Deploy uai-auth → valida `login`/`introspect` isolado (perfil prod, stub ainda no intel).
4. intel: PR modo introspection + introspector ajustado → build → **deploy intel** (vira pro real).
5. portal: PR provider real + refresh + `.env` → build → **deploy portal**.
6. Smoke e2e em prod; só então o stub está aposentado em prod (segue como dev).

## 10. Acceptance (testável)

- `POST /api/auth/login` (marley/vivian) → 200 com access RS256 (claims `sub/tenant_id/role/jti/exp/email`).
- Credencial inválida → 401.
- `/api/ooh/api/lines` com o access real → 200; após `logout`, mesmo token → 401 (blacklist via introspect).
- Refresh: access expirado (15min) → portal renova transparente sem deslogar a Vívian.
- `introspect` sem `X-UAI-Internal-Key` → 401.
- CI dos 3 repos verde; modo `stub` continua funcionando em dev.
- EPIC-002 acceptance criteria (vault) cumpridos no `uai-auth`.

## Referências

- [[adrs/adr-033-uai-auth]] · [[verticais/marketing-agency/epicos/epic-002-uai-auth-minimo|EPIC-002]] ·
  ADR-040 · ADR-036 · ADR-004 (intel sem tenant) · ADR-047 (Flyway no startup).
- Repos: `uai-auth` (criar) · `uai-ooh-intel` · `uai-portal` · `uai-infra`.
