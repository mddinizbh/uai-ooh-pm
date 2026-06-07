# uai-auth MVP + OOH cutover — MASTER runbook & corrections

> **Leia este doc PRIMEIRO.** Ele é a fonte autoritativa do esforço de auth. As **correções C1–C9**
> abaixo **sobrescrevem** o texto dos 4 planos por-repo onde houver conflito (foram apuradas por um
> crítico cross-repo sobre os drafts). O cutover (deploy intel/portal + smoke e2e) é **dono deste doc**.
>
> **Spec aprovada:** `docs/prd/2026-06-06-uai-auth-mvp-ooh-cutover-design.md`
> **Planos por-repo:**
> - `2026-06-06-uai-auth-service.md` (17 tasks) — construir o serviço
> - `2026-06-06-uai-infra-auth-wiring.md` (7 tasks) — compose/nginx/db/deploy/secrets
> - `2026-06-06-uai-ooh-intel-introspection.md` (3 tasks) — religar o intel
> - `2026-06-06-uai-portal-real-auth.md` (9 tasks) — provider real + refresh-on-401

## Ordem de execução (planos)

1. **uai-auth-service** — constrói o serviço (repo já existe como scaffold; ver C4). Termina com imagem no GHCR.
2. **uai-infra-auth-wiring** — compose + nginx `/api/auth/` + DB `uai_auth` + deploy + secrets (ver C1/C2/C3).
3. **Deploy isolado do uai-auth** (infra Task 7) — valida login/introspect com o intel ainda em `stub`.
4. **uai-ooh-intel-introspection** — código + PR (NÃO deploya ainda).
5. **uai-portal-real-auth** — código + PR (NÃO deploya ainda).
6. **Cutover** (§ Cutover abaixo) — deploy intel+portal **juntos** + smoke e2e.

---

## Correções autoritativas (sobrescrevem os planos por-repo)

### C1 🔴 — Encoding do keypair RS256 (prod-breaking se ignorado)
- Os secrets **`UAI_AUTH_JWT_PRIVATE_KEY`** e **`UAI_AUTH_JWT_PUBLIC_KEY`** guardam **base64 do PEM inteiro**,
  em **uma linha** (o `deploy.yml` faz `echo VAR=valor >> .env`, então PEM multilinha quebraria).
- **uai-auth `RsaKeyProvider`** (plano uai-auth Task 3): fazer **base64-decode do valor do env PRIMEIRO**
  → obtém o texto PEM → tira `BEGIN/END` → base64-decode do corpo → DER → `PKCS8EncodedKeySpec`(priv)/`X509EncodedKeySpec`(pub).
  (O draft parseava PEM cru, sem o decode externo — corrigir.)
- Os secrets vivem no **repo `uai-infra`** (é o `deploy.yml` dele que escreve o `.env` na VPS), **não** no uai-auth.
  → **Ignorar** o passo do uai-auth Task 17 que faz `gh secret set` no repo uai-auth. Geração (USER step):
  ```
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-priv.pem
  openssl rsa -in jwt-priv.pem -pubout -out jwt-pub.pem
  base64 -i jwt-priv.pem | tr -d '\n' | gh secret set UAI_AUTH_JWT_PRIVATE_KEY --repo mddinizbh/uai-infra
  base64 -i jwt-pub.pem  | tr -d '\n' | gh secret set UAI_AUTH_JWT_PUBLIC_KEY  --repo mddinizbh/uai-infra
  ```
  (no macOS é `base64 -i arquivo`; o `-w0` do GNU não existe — por isso o `tr -d '\n'`.)

### C2 — Nomes de env do Redis (alinhar os dois planos)
- **uai-auth `application.yml`**: `spring.data.redis.host: ${REDIS_HOST:localhost}` / `port: ${REDIS_PORT:6379}`.
- **uai-infra compose**: injeta **`REDIS_HOST=redis`** + **`REDIS_PORT=6379`** (NÃO `SPRING_DATA_REDIS_*`).
  Mantém o nome documentado no Dockerfile/README do uai-auth.

### C3 — Seed dos admins sem quebrar checksum do Flyway
- **Não** commitar placeholders literais (`__BCRYPT12_HASH_*__`) e editar depois — isso muda o checksum da V2
  e o Flyway falha no boot seguinte.
- Usar **Flyway placeholders** com **hífen nos dois lados** (Spring tira underscore das chaves de placeholder
  não-bracketadas — `${seed_marley_hash}` + `seed-marley-hash` **NÃO casa**; validado no IT do Task 5):
  `V2__seed_tenant_and_admins.sql` referencia `${seed-marley-hash}` / `${seed-vivian-hash}` (e os emails).
  Resolvidos em tempo de migração por `spring.flyway.placeholders.seed-marley-hash=${UAI_AUTH_SEED_MARLEY_HASH}`
  etc., alimentados por env.
  → o arquivo SQL é **estável** (sem drift de checksum); os hashes **nunca** entram no repo; chegam pela `.env`
  da VPS (secrets `UAI_AUTH_SEED_MARLEY_HASH` / `UAI_AUTH_SEED_VIVIAN_HASH` no **uai-infra**).
- Tenant "uAI" pode ficar literal na V2 (estável). Geração dos hashes (USER step, antes do deploy): rodar o
  `BcryptHashGeneratorTest` (gated por env `RAW_PASSWORD`) do plano uai-auth e por o hash no secret — **nunca** o texto puro.
- **Consequência boa (resolve C8):** a imagem do uai-auth fica **agnóstica de hash** (eles vêm do env em runtime),
  então **não** precisa rebuild/segunda-imagem após gerar os hashes.

### C4 — Repo uai-auth JÁ EXISTE (não criar)
- `mddinizbh/uai-auth` já existe (scaffold: `CLAUDE.md`, `main` vazio). **Não** rodar `gh repo create`
  (vai dar erro). Plano uai-auth Task 17 vira: adicionar `origin` (se faltar) e **push pro `main` existente**
  (convenção scaffold-from-template — remover o scaffold e subir o código). USER step.

### C5 — Pin Java 21 em todo `mvn` local do uai-auth
- Prefixar **todos** os `mvn` locais do plano uai-auth com `JAVA_HOME=$(/usr/libexec/java_home -v 21)`
  (igual o plano do intel; Java >21 do Homebrew quebra JaCoCo 0.8.12 e Lombok). Ex.:
  `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q verify`. (CI no GitHub usa `setup-java@21`, sem problema.)

### C6 🔴 — Deploy de intel/portal + smoke e2e (estava órfão; é dono deste doc)
- Nenhum plano por-repo deploya o intel/portal nem roda o smoke cross-service. Está no § **Cutover** abaixo.

### C7 🔴 — Janela intel↔portal: deploy junto + re-login único (não é outage)
- Trocar stub→real **invalida as sessões stub atuais** (o intel em introspection rejeita `iss=uai-auth-stub`).
  Comportamento esperado, não bug: usuário logado com stub recebe **um 401** → o portal limpa a sessão e
  redireciona pro login → ele loga com a **senha real**.
- Pra não criar janela assimétrica (intel novo + portal velho mintando stub, ou vice-versa), **deployar intel
  e portal no MESMO run** (`deploy-custom custom_services=uai-ooh-intel,uai-portal`). Avisar Marley/Vivian que
  **relogam uma vez** após o cutover.

### C8 — (resolvido por C3) imagem agnóstica de hash → sem gating de rebuild.

### C9 ✅ — Auth do `/revoke` (decidido 2026-06-06)
- **`POST /api/v1/auth/revoke` = só `X-UAI-Internal-Key`** (serviço confiável). **Remover** do uai-auth Task 12
  a exigência de Bearer ADMIN (`.hasRole('ADMIN')` + o matcher de bearer no `/revoke`). A camada "ADMIN" entra
  quando existir um admin UI chamando revoke.

---

## Cutover (dono deste doc — executar após os 4 planos)

> Pré-condições: uai-auth deployado e validado isolado (infra Task 7: `/api/auth/me` e bad-cred→401 ok);
> hashes seedados (C3) → `login` real funciona; intel e portal com PRs **mergeados** e **imagens rebuildadas** no GHCR.

1. **Merge** dos PRs do intel e do portal → esperar os CIs (build-push) ficarem verdes (imagens novas no GHCR).
2. **Deploy junto** (minimiza a janela):
   ```
   gh workflow run deploy.yml --repo mddinizbh/uai-infra -f action=deploy-custom -f custom_services=uai-ooh-intel,uai-portal
   ```
   (intel sobe em `mode=introspection`; portal sobe com `VITE_AUTH_MODE=sso`.)
3. **Smoke e2e em prod** (o teste que faltava — spec §8/§10):
   ```
   # login real (senha fora-de-banda; não colar em log compartilhado)
   ACCESS=$(curl -s -X POST https://uaiagencia.com.br/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"vivian.cristina@bhbusmidia.com.br","password":"<senha>"}' | jq -r .access_token)
   # Bearer real no intel → 200
   curl -s -o /dev/null -w '%{http_code}\n' https://uaiagencia.com.br/api/ooh/api/lines -H "Authorization: Bearer $ACCESS"   # 200
   # logout → mesma chamada → 401 (revogação via introspect/blacklist)
   curl -s -X POST https://uaiagencia.com.br/api/auth/logout -H "Authorization: Bearer $ACCESS"
   curl -s -o /dev/null -w '%{http_code}\n' https://uaiagencia.com.br/api/ooh/api/lines -H "Authorization: Bearer $ACCESS"   # 401
   ```
4. **Confirmar no browser:** abrir `uaiagencia.com.br` (aba anônima), logar com a senha real, ver o catálogo/cesta carregar.
5. **Aposentar o stub em prod:** confirmado que o real funciona, o intel `stub` e o portal `stubProvider` ficam
   só como modo **dev** (não apagados).

## Itens menores / defaults (não bloqueiam; registrados)

- **JWKS** (`/.well-known/jwks`): construído mas **sem consumidor no MVP** (o intel usa introspection). Não expor no
  nginx por ora; expor quando algum cliente precisar de validação local.
- **Botão "Entrar com uAI" (SSO/IdP redirect)** no portal: **escondido** em modo `sso` (o MVP não tem `/authorize`);
  o login real é o form email/senha.
- **Janela 7d deslizante** no refresh: aceita (reset `expiresAt=now+7d` a cada rotação). Sem cap absoluto no MVP.
- **`roleFromString` fallback** no portal: role inesperada → `CLIENT_VIEWER` (read-only). ADMIN = acesso total.
- **Placeholders/granularidade menores** apontados pelo crítico (uai-auth Task 12 sem teste isolado, Task 14
  verificação-only, Task 1 com 2 versões do `AuthApplication`, intel Task 1 agrupando 5 edits, portal Task 6 com
  red TDD frouxo): tolerados; o executor deve manter a disciplina red→green e quebrar a intel Task 1 em sub-passos.
