# uai-infra Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (- [ ]) syntax.

**Goal:** Wire the new `uai-auth` service into the uAI production infra (compose service, nginx `/api/auth/` route, `uai_auth` database, deploy pipeline, and the RS256 keypair secrets) so the OOH auth cutover can replace the interim stubs with real authentication.

**Architecture:** `uai-infra` is a **declarative infra repo** — there is no application code. The VPS runs everything from `docker-compose.yml`; `nginx.conf` is the same-origin reverse proxy (every upstream uses the `set $up ...; proxy_pass http://$up` dynamic-resolver pattern so a missing container returns 502 instead of crash-looping); `.github/workflows/deploy.yml` SSHes into the VPS, regenerates `.env` from GitHub Secrets, `curl`s `docker-compose.yml`+`nginx.conf` fresh from the repo, and recreates services via the `deploy_service` helper. This plan adds `uai-auth` to all four touchpoints. The RSA private/public keys are base64-encoded single-line PEM in GitHub Secrets (a multiline PEM cannot survive the line-by-line `.env` generation).

**Tech Stack:** Docker Compose v2, nginx:alpine, postgres:16-alpine, GitHub Actions (`workflow_dispatch` + `appleboy/ssh-action`), `gh` CLI, `openssl`. No build system (declarative YAML/conf only). Validation = `docker compose config`, `nginx -t` (in a container with throwaway certs), and a live `deploy-custom` + `curl` smoke.

---

## File Structure

| File | Status | Single responsibility |
|---|---|---|
| `docker-compose.yml` | Modify (2 edits) | (a) `postgres-init` entrypoint creates database `uai_auth`; (b) new `uai-auth` service block (image, :8084, profile prod, DB/Redis/keypair env, depends_on, 384M) |
| `nginx.conf` | Modify (1 edit) | New `location /api/auth/` in the `uaiagencia.com.br` :443 server, rewriting `^/api/auth/(.*)$ → /api/v1/auth/$1` to `uai-auth:8084` via the `set $up_auth` resolver pattern |
| `.github/workflows/deploy.yml` | Modify (2 edits) | (a) write `UAI_AUTH_JWT_PRIVATE_KEY` + `UAI_AUTH_JWT_PUBLIC_KEY` into the VPS `.env`; (b) `deploy_service uai-auth` inside `deploy_apps()` |
| `.env.example` | Modify (1 edit) | Document the two new `UAI_AUTH_JWT_*` vars + how to generate them (base64 PEM) |

**No files are created.** All four are existing files modified in place. `uai-auth` itself is a separate repo/plan; this repo only references its published image `ghcr.io/${GITHUB_USER}/uai-auth:latest`.

---

### Task 1: USER prerequisites — RSA keypair + GitHub Secrets (manual, run by the human)

> **! These are USER steps — the agent/classifier is blocked from generating private keys and from `gh repo create`/bulk push. Paste these commands to the user and wait for confirmation before Task 7.** No repo files change here. The `mddinizbh/uai-auth` repo already exists (scaffold); its CI + first `:latest` image push is the separate uai-auth plan and gates Task 7.

**Files:** none (off-repo: GitHub Secrets on `mddinizbh/uai-infra`).
**Test:** `gh secret list --repo mddinizbh/uai-infra | grep -E 'UAI_AUTH_JWT_(PRIVATE|PUBLIC)_KEY'` returns both names.

- [ ] **! USER step** — Generate the RS256 keypair (PKCS#8 private + SPKI public) into `/tmp` (PEMs never enter the repo; `*.pem` is gitignored anyway):
  ```bash
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/uai_auth_jwt_private.pem
  openssl rsa -in /tmp/uai_auth_jwt_private.pem -pubout -out /tmp/uai_auth_jwt_public.pem
  ```
- [ ] **! USER step** — Base64-encode each PEM to a single line and set them as Secrets **on the uai-infra repo** (the keys are consumed by infra's `deploy.yml`, not by uai-auth's CI):
  ```bash
  base64 < /tmp/uai_auth_jwt_private.pem | tr -d '\n' | gh secret set UAI_AUTH_JWT_PRIVATE_KEY --repo mddinizbh/uai-infra
  base64 < /tmp/uai_auth_jwt_public.pem  | tr -d '\n' | gh secret set UAI_AUTH_JWT_PUBLIC_KEY  --repo mddinizbh/uai-infra
  ```
- [ ] **! USER step** — Verify both secrets exist and wipe the local PEMs:
  ```bash
  gh secret list --repo mddinizbh/uai-infra | grep -E 'UAI_AUTH_JWT_(PRIVATE|PUBLIC)_KEY'
  rm -f /tmp/uai_auth_jwt_private.pem /tmp/uai_auth_jwt_public.pem
  ```
  Expected: two lines listing `UAI_AUTH_JWT_PRIVATE_KEY` and `UAI_AUTH_JWT_PUBLIC_KEY`. `UAI_INTERNAL_API_KEY` already exists (do not regenerate it).
- [ ] **! USER note** — Repo `mddinizbh/uai-auth` already exists; building its CI image (`ghcr.io/mddinizbh/uai-auth:latest`) is handled by the separate uai-auth plan and **must be green before Task 7**.

---

### Task 2: Create the `uai_auth` database in `postgres-init`

The main `postgres` (postgres:16-alpine, owned by `${PG_USER}`) already gets `uai_tokenmetrics` and `uai_buslines` created idempotently by the `postgres-init` one-shot container. `uai_auth` lives in this same instance (uai-auth's Flyway creates the tables + seed on startup). Add one idempotent `CREATE DATABASE` block following the exact existing pattern.

**Files:** Modify `docker-compose.yml` (the `postgres-init` entrypoint heredoc, lines 75–81).
**Test:** `grep -q 'CREATE DATABASE uai_auth' docker-compose.yml` AND `docker compose --profile prod config -q` exits 0.

- [ ] Step: Create the work branch and write the failing test (run from the repo root):
  ```bash
  git -C /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra checkout -b feat/uai-auth-infra
  grep -q 'CREATE DATABASE uai_auth' /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra/docker-compose.yml
  ```
- [ ] Step: Run it, verify it FAILS:
  ```bash
  grep -q 'CREATE DATABASE uai_auth' /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra/docker-compose.yml; echo "exit=$?"
  ```
  Expected: `exit=1` (string absent — the database is not created yet).
- [ ] Step: Minimal implementation — in `docker-compose.yml`, insert the `uai_auth` block **between** the `uai_buslines` block and `echo "=== Databases finais ==="` (after line 78). Replace this exact existing text:
  ```yaml
          echo "=== Garantindo database uai_buslines ==="
          psql -v ON_ERROR_STOP=1 -d postgres -tc "SELECT 1 FROM pg_database WHERE datname='uai_buslines'" | grep -q 1 \
            && echo "uai_buslines ja existe" \
            || psql -v ON_ERROR_STOP=1 -d postgres -c "CREATE DATABASE uai_buslines;" && echo "uai_buslines criado"
          echo "=== Databases finais ==="
  ```
  with (adds the `uai_auth` block, keeps everything else identical):
  ```yaml
          echo "=== Garantindo database uai_buslines ==="
          psql -v ON_ERROR_STOP=1 -d postgres -tc "SELECT 1 FROM pg_database WHERE datname='uai_buslines'" | grep -q 1 \
            && echo "uai_buslines ja existe" \
            || psql -v ON_ERROR_STOP=1 -d postgres -c "CREATE DATABASE uai_buslines;" && echo "uai_buslines criado"
          echo "=== Garantindo database uai_auth ==="
          psql -v ON_ERROR_STOP=1 -d postgres -tc "SELECT 1 FROM pg_database WHERE datname='uai_auth'" | grep -q 1 \
            && echo "uai_auth ja existe" \
            || psql -v ON_ERROR_STOP=1 -d postgres -c "CREATE DATABASE uai_auth;" && echo "uai_auth criado"
          echo "=== Databases finais ==="
  ```
- [ ] Step: Run the tests, verify PASS:
  ```bash
  grep -q 'CREATE DATABASE uai_auth' /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra/docker-compose.yml && echo "grep OK"
  cd /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra && cp -n .env.example .env 2>/dev/null; docker compose --profile prod config -q && echo "compose config OK"
  ```
  Expected: `grep OK` and `compose config OK` (config may print benign `variable is not set` warnings to stderr for unset secrets — exit code is 0). The local `.env` is gitignored.
- [ ] Step: (optional, deterministic) Prove the SQL itself works against a throwaway Postgres:
  ```bash
  docker run -d --rm --name auth-db-test -e POSTGRES_USER=t -e POSTGRES_PASSWORD=t -e POSTGRES_DB=uai_cms postgres:16-alpine >/dev/null
  until docker exec auth-db-test pg_isready -U t -d uai_cms >/dev/null 2>&1; do sleep 1; done
  docker exec -e PGUSER=t -e PGPASSWORD=t auth-db-test psql -v ON_ERROR_STOP=1 -d postgres -c "CREATE DATABASE uai_auth;"
  docker exec -e PGUSER=t -e PGPASSWORD=t auth-db-test psql -d postgres -lqt | cut -d'|' -f1 | grep -qw uai_auth && echo "uai_auth created OK"
  docker rm -f auth-db-test >/dev/null
  ```
  Expected: `CREATE DATABASE` then `uai_auth created OK`.
- [ ] Step: Commit:
  ```bash
  cd /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra && git add docker-compose.yml && git commit -m "feat(db): cria database uai_auth no postgres-init

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

### Task 3: Add the `uai-auth` compose service

Adds the service block per the cross-repo contract: image `ghcr.io/${GITHUB_USER}/uai-auth:latest`, port 8084 (internal only), `profiles: [prod]`, `uai_auth` DB on the main `postgres`, Redis, the keypair env + internal key, `depends_on` postgres+redis healthy, 384M. No host port is exposed (reached only via nginx).

**Files:** Modify `docker-compose.yml` (insert a new service between the `uai-cms` block — ends line 394 — and the `# ── uai-core` comment at line 396).
**Test:** `docker compose --profile prod config --services | grep -qx uai-auth` AND `docker compose --profile prod config | grep -q 'ghcr.io/.*uai-auth:latest'`.

- [ ] Step: Write the failing test:
  ```bash
  cd /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra && docker compose --profile prod config --services | grep -qx uai-auth; echo "exit=$?"
  ```
- [ ] Step: Run it, verify it FAILS:
  Expected: `exit=1` (no service named `uai-auth` yet).
- [ ] Step: Minimal implementation — in `docker-compose.yml`, insert this block immediately **before** the line `  # ── uai-core ────────────────────────────` (line 396), right after the `uai-cms` service's closing `memory: 768M` (line 394):
  ```yaml
    # ── uai-auth (autenticação real — login/refresh/introspect; aposenta o stub OOH) ──
    uai-auth:
      image: ghcr.io/${GITHUB_USER}/uai-auth:latest
      container_name: uai-auth
      restart: unless-stopped
      networks: [uai-net]
      # cross-cutting; o deploy é sempre por nome (deploy_service) → o profile prod NÃO bloqueia
      profiles: ["prod"]
      # Sem porta exposta — só via Nginx (https://uaiagencia.com.br/api/auth/ → /api/v1/auth/)
      environment:
        SERVER_PORT: 8084
        SPRING_PROFILES_ACTIVE: prod
        # uai_auth vive no postgres principal (mesmo do cms), não no postgres-ooh
        DB_HOST: postgres
        DB_PORT: 5432
        DB_NAME: uai_auth
        DB_USER: ${PG_USER}
        DB_PASSWORD: ${PG_PASSWORD}
        SPRING_DATA_REDIS_HOST: redis
        SPRING_DATA_REDIS_PORT: 6379
        # Keypair RS256 em PEM base64 single-line (o app decodifica base64). Private = GitHub Secret.
        UAI_AUTH_JWT_PRIVATE_KEY: ${UAI_AUTH_JWT_PRIVATE_KEY}
        UAI_AUTH_JWT_PUBLIC_KEY: ${UAI_AUTH_JWT_PUBLIC_KEY}
        # Internal API key (ADR-040) — mesmo secret que o intel usa pra introspect
        UAI_INTERNAL_API_KEY: ${UAI_INTERNAL_API_KEY}
      depends_on:
        postgres:
          condition: service_healthy
        redis:
          condition: service_healthy
      deploy:
        resources:
          limits:
            memory: 384M

  ```
- [ ] Step: Run the tests, verify PASS:
  ```bash
  cd /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra
  docker compose --profile prod config --services | grep -qx uai-auth && echo "service OK"
  docker compose --profile prod config | grep -q 'ghcr.io/.*uai-auth:latest' && echo "image OK"
  docker compose --profile prod config -q && echo "compose valid"
  ```
  Expected: `service OK`, `image OK`, `compose valid`.
- [ ] Step: Commit:
  ```bash
  cd /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra && git add docker-compose.yml && git commit -m "feat(compose): serviço uai-auth (:8084, profile prod, DB uai_auth, keypair RS256)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

### Task 4: Add the nginx `/api/auth/` route

Add a `location /api/auth/` to the main `uaiagencia.com.br` :443 server that strips `/api/auth` to `/api/v1/auth` and proxies to `uai-auth:8084`, using the file's mandatory `set $up_...; proxy_pass http://$up_...` dynamic-resolver pattern (a literal hostname in `proxy_pass` would crash-loop nginx if `uai-auth` is down during a staged deploy). The front calls `/api/auth/login` → nginx rewrites to `/api/v1/auth/login`.

**Files:** Modify `nginx.conf` (insert inside the `server { server_name uaiagencia.com.br ... }` block, between the front `location /` closing brace at line 58 and the `# APIs — /api/cms/` comment at line 60).
**Test:** `grep -q 'location /api/auth/' nginx.conf` (red→green) AND a full `nginx -t` passes inside an nginx container with throwaway certs.

- [ ] Step: Write the failing test:
  ```bash
  grep -q 'location /api/auth/' /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra/nginx.conf; echo "exit=$?"
  ```
- [ ] Step: Run it, verify it FAILS:
  Expected: `exit=1` (no `/api/auth/` route yet).
- [ ] Step: Minimal implementation — in `nginx.conf`, replace this exact existing text (front block close + the cms comment):
  ```nginx
          proxy_set_header X-Forwarded-Proto $scheme;
      }

      # APIs — /api/cms/ → uai-cms /api/
  ```
  with (inserts the new auth location between them):
  ```nginx
          proxy_set_header X-Forwarded-Proto $scheme;
      }

      # /api/auth/ → uai-auth /api/v1/auth/ (login/refresh/logout/me — auth real, aposenta o stub)
      # strip+remap: o front usa VITE_AUTH_BASE_URL=/api/auth e chama /login ⇒ /api/auth/login → /api/v1/auth/login
      location /api/auth/ {
          set $up_auth uai-auth:8084;
          rewrite ^/api/auth/(.*)$ /api/v1/auth/$1 break;
          proxy_pass http://$up_auth;
          proxy_set_header Host $host;
          proxy_set_header X-Real-IP $remote_addr;
          proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
          proxy_set_header X-Forwarded-Proto $scheme;
      }

      # APIs — /api/cms/ → uai-cms /api/
  ```
- [ ] Step: Run the tests, verify PASS — grep, then a real `nginx -t` against the file with throwaway certs (the live config references Let's Encrypt cert paths that don't exist locally, so we mount a self-signed stand-in over `/etc/letsencrypt`):
  ```bash
  cd /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra
  grep -q 'location /api/auth/' nginx.conf && echo "grep OK"
  TD=$(mktemp -d); mkdir -p "$TD/live/uaiagencia.com.br"
  openssl req -x509 -newkey rsa:2048 -nodes -days 1 \
    -keyout "$TD/live/uaiagencia.com.br/privkey.pem" \
    -out    "$TD/live/uaiagencia.com.br/fullchain.pem" -subj "/CN=test" >/dev/null 2>&1
  : > "$TD/options-ssl-nginx.conf"
  openssl dhparam -out "$TD/ssl-dhparams.pem" 1024 >/dev/null 2>&1
  docker run --rm \
    -v "$PWD/nginx.conf:/etc/nginx/conf.d/default.conf:ro" \
    -v "$TD:/etc/letsencrypt:ro" \
    nginx:alpine nginx -t
  rm -rf "$TD"
  ```
  Expected: `grep OK`, then nginx prints `nginx: configuration file /etc/nginx/conf.d/default.conf test is successful` and exits 0 (proves the new `location` block has no syntax error and the whole file still parses). The 1024-bit dhparam is test-only.
- [ ] Step: Commit:
  ```bash
  cd /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra && git add nginx.conf && git commit -m "feat(nginx): rota /api/auth/ → uai-auth:8084 (strip /api/auth → /api/v1/auth)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

### Task 5: Wire `deploy.yml` — write keypair to `.env` + deploy `uai-auth`

Two edits in the deploy workflow: (a) inside the `GERAR .env` block, write the two new keypair secrets into the VPS `.env` (so compose can inject them; `UAI_INTERNAL_API_KEY` is already written at line 91); (b) inside `deploy_apps()`, add `deploy_service uai-auth` before the intel/portal apps (auth is a resource service the others introspect against; it must be up first in the cutover).

**Files:** Modify `.github/workflows/deploy.yml` (`GERAR .env` block ~line 91; `deploy_apps()` ~lines 215–216).
**Test:** `grep -q 'UAI_AUTH_JWT_PRIVATE_KEY=' deploy.yml` AND `grep -q 'deploy_service uai-auth' deploy.yml` AND the file is valid YAML.

- [ ] Step: Write the failing test:
  ```bash
  F=/Users/marleydiniz/IdeaProjects/personal/uai/uai-infra/.github/workflows/deploy.yml
  grep -q 'UAI_AUTH_JWT_PRIVATE_KEY=' "$F" && grep -q 'deploy_service uai-auth' "$F"; echo "exit=$?"
  ```
- [ ] Step: Run it, verify it FAILS:
  Expected: `exit=1` (neither the secret echo nor the deploy call exist yet).
- [ ] Step: Minimal implementation, edit 1 — in `deploy.yml`, in the `GERAR .env` block, replace this exact existing text:
  ```yaml
              echo "UAI_INTERNAL_API_KEY=${{ secrets.UAI_INTERNAL_API_KEY }}" >> .env
              echo "LETSENCRYPT_EMAIL=${{ secrets.LETSENCRYPT_EMAIL }}" >> .env
  ```
  with (adds the two keypair lines after the existing internal-key line):
  ```yaml
              echo "UAI_INTERNAL_API_KEY=${{ secrets.UAI_INTERNAL_API_KEY }}" >> .env
              echo "UAI_AUTH_JWT_PRIVATE_KEY=${{ secrets.UAI_AUTH_JWT_PRIVATE_KEY }}" >> .env
              echo "UAI_AUTH_JWT_PUBLIC_KEY=${{ secrets.UAI_AUTH_JWT_PUBLIC_KEY }}" >> .env
              echo "LETSENCRYPT_EMAIL=${{ secrets.LETSENCRYPT_EMAIL }}" >> .env
  ```
- [ ] Step: Minimal implementation, edit 2 — in `deploy.yml`, inside `deploy_apps()`, replace this exact existing text:
  ```bash
                deploy_service uai-cms
                # F1 OOH — intel (API) + portal (front; substitui o spark na raiz via nginx)
                deploy_service uai-ooh-intel
  ```
  with (adds the auth deploy before intel/portal):
  ```bash
                deploy_service uai-cms
                # uai-auth — autenticação real; sobe antes do intel/portal (eles introspectam nele)
                deploy_service uai-auth
                # F1 OOH — intel (API) + portal (front; substitui o spark na raiz via nginx)
                deploy_service uai-ooh-intel
  ```
- [ ] Step: Run the tests, verify PASS (grep both + validate YAML):
  ```bash
  F=/Users/marleydiniz/IdeaProjects/personal/uai/uai-infra/.github/workflows/deploy.yml
  grep -q 'UAI_AUTH_JWT_PRIVATE_KEY=' "$F" && grep -q 'UAI_AUTH_JWT_PUBLIC_KEY=' "$F" && echo "secrets OK"
  grep -q 'deploy_service uai-auth' "$F" && echo "deploy OK"
  python3 -c "import yaml,sys; yaml.safe_load(open('$F')); print('YAML valid')"
  ```
  Expected: `secrets OK`, `deploy OK`, `YAML valid`.
- [ ] Step: Commit:
  ```bash
  cd /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra && git add .github/workflows/deploy.yml && git commit -m "ci(deploy): injeta keypair uai-auth no .env + deploy_service uai-auth

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

### Task 6: Document the new vars in `.env.example`

Keep `.env.example` honest about every var compose references. Add a `uai-auth` section documenting the two base64-PEM keypair vars and exactly how to generate them.

**Files:** Modify `.env.example` (insert after the `UAI_INTERNAL_API_KEY` block, line 50).
**Test:** `grep -q 'UAI_AUTH_JWT_PRIVATE_KEY=' .env.example` AND `grep -q 'UAI_AUTH_JWT_PUBLIC_KEY=' .env.example`.

- [ ] Step: Write the failing test:
  ```bash
  grep -q 'UAI_AUTH_JWT_PRIVATE_KEY=' /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra/.env.example; echo "exit=$?"
  ```
- [ ] Step: Run it, verify it FAILS:
  Expected: `exit=1` (not documented yet).
- [ ] Step: Minimal implementation — in `.env.example`, replace this exact existing text:
  ```bash
  # ── BH Bus Lines ────────────────────────────────────────
  # Protege o endpoint interno de reimport (gere com: openssl rand -hex 32)
  UAI_INTERNAL_API_KEY=TROQUE_AQUI_gere_com_openssl_rand_hex_32
  ```
  with (adds the uai-auth keypair section right after — `UAI_INTERNAL_API_KEY` is shared with uai-auth):
  ```bash
  # ── BH Bus Lines / uai-auth (internal key compartilhado, ADR-040) ──
  # Protege endpoints internos (buslines /import; uai-auth /introspect e /revoke).
  # Gere com: openssl rand -hex 32
  UAI_INTERNAL_API_KEY=TROQUE_AQUI_gere_com_openssl_rand_hex_32

  # ── uai-auth (JWT RS256) ────────────────────────────────
  # Keypair RS256 em PEM, base64-encoded SINGLE-LINE — o app uai-auth decodifica base64.
  # (PEM multilinha não sobrevive ao .env gerado linha-a-linha pelo deploy.yml.)
  # Gerar:
  #   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt_private.pem
  #   openssl rsa -in jwt_private.pem -pubout -out jwt_public.pem
  #   base64 < jwt_private.pem | tr -d '\n'   →  UAI_AUTH_JWT_PRIVATE_KEY  (GitHub Secret no uai-infra)
  #   base64 < jwt_public.pem  | tr -d '\n'   →  UAI_AUTH_JWT_PUBLIC_KEY
  UAI_AUTH_JWT_PRIVATE_KEY=base64_do_PEM_da_chave_privada
  UAI_AUTH_JWT_PUBLIC_KEY=base64_do_PEM_da_chave_publica
  ```
- [ ] Step: Run the tests, verify PASS:
  ```bash
  E=/Users/marleydiniz/IdeaProjects/personal/uai/uai-infra/.env.example
  grep -q 'UAI_AUTH_JWT_PRIVATE_KEY=' "$E" && grep -q 'UAI_AUTH_JWT_PUBLIC_KEY=' "$E" && echo "documented OK"
  ```
  Expected: `documented OK`.
- [ ] Step: Commit:
  ```bash
  cd /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra && git add .env.example && git commit -m "docs(env): documenta UAI_AUTH_JWT_PRIVATE_KEY/PUBLIC (PEM base64)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
  ```

---

### Task 7: Deploy `uai-auth` + nginx route, then smoke the `/api/auth/` route

> **BLOCKED until (a) Task 1 secrets are set, AND (b) `ghcr.io/mddinizbh/uai-auth:latest` exists in GHCR (the uai-auth repo CI is green — separate plan, cutover §9 step 1). The deploy workflow operates live on the VPS — there is no true dry-run.** Local validation is the offline config check; the real validation is a `deploy-custom` run + `curl`.

**Files:** none (operational: branch merge + workflow_dispatch + curl).
**Test:** `curl` to `https://uaiagencia.com.br/api/auth/me` returns `401` (no Bearer) — proving nginx routes `/api/auth/*` to a live `uai-auth`.

- [ ] Step: Local "dry" validation (offline, before any live deploy) — confirm the service resolves in the prod-profile config and that nothing else regressed:
  ```bash
  cd /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra
  docker compose --profile prod config --services | grep -qx uai-auth && echo "uai-auth in config"
  docker compose --profile prod config -q && echo "full config valid"
  ```
  Expected: `uai-auth in config`, `full config valid`. (A local `docker compose pull uai-auth` is intentionally NOT run here — the image is pulled server-side during deploy with GHCR auth.)
- [ ] Step: Open the PR and merge to `main` (deploy.yml `curl`s `docker-compose.yml`+`nginx.conf` from `?ref=main`, so the edits must be on `main` before deploy):
  ```bash
  cd /Users/marleydiniz/IdeaProjects/personal/uai/uai-infra
  git push -u origin feat/uai-auth-infra
  gh pr create --repo mddinizbh/uai-infra --base main --head feat/uai-auth-infra \
    --title "feat: wire uai-auth into infra (compose/nginx/db/deploy/secrets)" \
    --body "Adds uai-auth service (:8084, profile prod, DB uai_auth, RS256 keypair), nginx /api/auth/ route, uai_auth DB in postgres-init, deploy_service uai-auth, and the UAI_AUTH_JWT_* secrets wiring. Part of the uai-auth MVP + OOH auth cutover.

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
  gh pr merge --repo mddinizbh/uai-infra --squash --delete-branch
  ```
- [ ] **! USER/gated step** — Verify the uai-auth image is published, then trigger the real deploy of the DB init, the new service, and an nginx reload (one `deploy-custom` run; `postgres-init` recreation runs the `CREATE DATABASE uai_auth`, `nginx` recreation picks up the new route):
  ```bash
  gh api repos/mddinizbh/uai-auth/packages 2>/dev/null || gh api "users/mddinizbh/packages?package_type=container" --jq '.[].name' | grep -x uai-auth
  gh workflow run deploy.yml --repo mddinizbh/uai-infra \
    -f action=deploy-custom -f custom_services=postgres-init,uai-auth,nginx
  gh run watch --repo mddinizbh/uai-infra "$(gh run list --repo mddinizbh/uai-infra --workflow deploy.yml --limit 1 --json databaseId --jq '.[0].databaseId')"
  ```
  Expected: the run log shows `[postgres-init] OK`, `[uai-auth] OK`, `[nginx] OK` and a final status list including `uai-auth ... Up`.
- [ ] Step: Smoke the route — both calls prove nginx strips `/api/auth` → `/api/v1/auth` and reaches a live `uai-auth`:
  ```bash
  curl -s -o /dev/null -w 'me=%{http_code}\n' https://uaiagencia.com.br/api/auth/me
  curl -s -o /dev/null -w 'login=%{http_code}\n' -X POST https://uaiagencia.com.br/api/auth/login \
    -H 'Content-Type: application/json' -d '{"email":"nobody@example.com","password":"wrong"}'
  ```
  Expected: `me=401` (no Bearer) and `login=401` (bad credentials). Any `502` means `uai-auth` isn't up (check the image/env); a `404` means the nginx route didn't reload (re-run `deploy-custom` with `nginx`).
- [ ] **! USER step** — Full acceptance (real password, out-of-band): `curl -X POST .../api/auth/login` with a real admin credential → `200` with `access_token`/`refresh_token`. This is run by the user since plaintext passwords never live in the repo/plan.
