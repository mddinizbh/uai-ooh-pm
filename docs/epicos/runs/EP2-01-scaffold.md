# Run — EP2-01 (intel: scaffold + conexão ao serving) · 2026-06-05 · ✅ DONE

> Execução no `uai-ooh-intel` (branch `feat/ooh-ep2-01`), bootando o serviço **intel** a partir do
> `uai-ooh-service-template` (hexagonal single-module), conectado ao banco `ooh` como **read-only
> `ooh_intel_ro`** (SELECT só em `serving`), lendo por `dataset_version` **ACTIVE**, com health.
> Estado **verificado no banco `ooh`** via MCP `postgres-ooh` em 2026-06-05, contra serving **v5 ACTIVE**.
> Task do mapa F1: `docs/epicos/bloco1/f1/02-intel-backend/EP2-01-scaffold.md`.
>
> **Veredito:** os 4 critérios de pronto passam — scaffold regenerado limpo do template (pacote
> `com.uai.ooh.intel`, `artifactId` `uai-ooh-intel`, porta `:8085`), datasource read-only via
> `ooh_intel_ro` com least-privilege confirmado no banco, `ActiveVersionResolver` resolvendo a ACTIVE,
> build compila/empacota e **0 `ST_*` no runtime**. Review aprovou; testes 19/19; a rodada de refute
> **não derrubou** o done. → **DONE**. Base destrava EP2-02..09.

## Critério de pronto vs. medido (banco `ooh` + build, serving v5 ACTIVE)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| Conecta via `ooh_intel_ro` (SELECT só em `serving`) | role faz login + least-privilege | `ooh_intel_ro` `rolcanlogin=t`; SELECT nas 9 tabelas de `serving`; **USAGE negada** em `core`/`raw`/`analise` | ✅ |
| `serving.dataset_version` ACTIVE | exatamente 1 ACTIVE | **1 ACTIVE** (`version_id=5`, `label='serving-f1'`, `core_version_id=1`, `activated_at=2026-06-05 22:44 UTC`) | ✅ |
| `version_id` ACTIVE resolvido + logado no boot | resolve e loga | `ActiveVersionResolver` + `JdbcDatasetVersionRepository` (SQL `serving.dataset_version WHERE status='ACTIVE'`, cache curto); log no boot | ✅ |
| `/actuator/health` com indicator `dataset_version` ACTIVE | indicator verde | `HealthIndicator` custom (conexão `ooh` + existência de ACTIVE); verificado por review + código (não bootado live contra VPS — alvo dev = serving local) | ✅ |
| Build compila | compile + package OK | `./mvnw -DskipTests compile` ⇒ COMPILE_OK; `package` ⇒ `target/uai-ooh-intel-0.0.1-SNAPSHOT.jar` (53 MB) | ✅ |
| Sem PostGIS/`ST_*` no runtime (ADR-003) | 0 `ST_*` | repositório usa **SELECT simples via `JdbcTemplate`**; nenhum `ST_*` introduzido | ✅ |
| Pacote hexagonal pronto p/ domínio (tarefa 2) | single-module hexagonal renomeado | `com.uai.ooh.intel`, `artifactId=uai-ooh-intel`, porta `:8085`, `ddl-auto=none`, sem Flyway, profiles `local`/`prod`, HikariCP pool `ooh-intel-ro` | ✅ |
| Composite check ACTIVE (`active_n` / `active_with_activated_at` / `active_line_n`) | 1 / 1 / `serving.line` populado na versão viva | `active_n=1`, `active_with_activated_at=1`, `active_line_n` > 0 (serving.line na v5 ACTIVE) | ✅ |

## Build / testes (escopo declarado)

- **Compilação:** `./mvnw -DskipTests compile` ⇒ COMPILE_OK; `./mvnw -DskipTests package` ⇒
  `uai-ooh-intel-0.0.1-SNAPSHOT.jar` (53 MB). **Suite completa não rodada no estágio implement** (ficou
  pro estágio Test).
- **Suite (estágio Test):** `mvn verify` com **JDK 21 forçado** (`JAVA_HOME=<JDK21>`) — o `mvn` default
  do sistema roda sob Java 26. **19/19 passou** (0 falhas, 0 skips), incluindo os **7 testes novos**
  esperados (7 criados = 7 esperados).

## Decisões / desvios

- **Regeração limpa do template (convenção EP2+).** Scaffold gerado do `uai-ooh-service-template` atual
  (template usa `com.uai.ooh.template`) e renomeado para `com.uai.ooh.intel` / `uai-ooh-intel` / `:8085`
  — **não** evoluído sobre scaffold velho. Mesma convenção vale pra todo repo gerado do template.
- **Alvo de dev = serving LOCAL** (decisão da task, 2026-06-05): ITs com pg local; **prod** aponta pro
  `ooh` do VPS via profile. Por isso o `/actuator/health` foi validado por review/código, não por boot
  live contra o VPS.
- **Desvio menor no harness de build (a reconciliar):** o implement reporta uso do wrapper
  (`./mvnw -DskipTests compile/package`), mas o tester reporta **"sem wrapper `./mvnw` no repo"** e
  forçou `mvn` com JDK 21 porque o `mvn` default cai em Java 26. Não afeta o veredito (compile + 19/19
  passaram), mas vale **fixar Java 21 e padronizar wrapper** no repo pra não depender de `JAVA_HOME`
  manual. Carimbar na EP2-09 (testes+deploy).
- **Intel só LÊ.** `ddl-auto=none`, sem Flyway — o `serving` é do normalizer (EP1). Este scaffold não
  cria/promove `dataset_version`; apenas resolve e filtra pela ACTIVE.
- **Limite de evidência:** o payload do implement não enumerou os arquivos criados (`files_created` veio
  vazio/truncado), mas review + test + checks no banco confirmam que o scaffold existe, compila e conecta.

## Estado do `dataset_version`

- `serving.dataset_version`: **v5 ACTIVE** (`label='serving-f1'`, `core_version_id=1`, `activated_at`
  `2026-06-05 22:44 UTC`) — exatamente **1 ACTIVE**, com `activated_at` preenchido. ✅
- O intel lê essa versão como consumidor read-only; **não altera** o ciclo de versões (coerente com
  ADR-052, fronteira dados↔consumidor). A v5 é a mesma versão viva consumida pela EP1-02.

## Adversarial — o que o cético tentou (refuted: false)

- **Vetor 1 — "GATE/critério de pronto não batido".** Releu o "Critério de pronto" do
  `EP2-01-scaffold.md` e confrontou item a item com banco + código. **Não derrubou:**
  (1) `ooh_intel_ro` faz login (`pg_roles.rolcanlogin=t`) com SELECT **apenas** em `serving` (9 tabelas)
  e **USAGE negada** em `core`/`raw`/`analise` — least-privilege confirmado;
  (2) `ActiveVersionResolver` + `JdbcDatasetVersionRepository` carregam a ACTIVE via
  `SELECT ... serving.dataset_version WHERE status='ACTIVE'` (1 ACTIVE = `version_id=5`);
  (3) **0 `ST_*`** no caminho de leitura (JdbcTemplate puro);
  (4) build compila e empacota o jar. Gate de scaffold: **sobreviveu**.
- **Limites reconhecidos (não derrubam o done):**
  - **Suite completa não exercitada no implement** — os 19/19 (com 7 novos) só correram no estágio Test.
  - **Health não bootado live contra o VPS** — validado por review/código sob a decisão "alvo dev =
    serving local"; o smoke real contra o `ooh` do VPS fica pro deploy (EP2-09).
  - **Wrapper/JDK não padronizados** — divergência implement (`./mvnw`) × tester (`mvn` + JDK 21 forçado);
    funciona, mas é frágil até fixar Java 21 + wrapper no repo.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`uai-ooh-pm/docs/epicos/runs/EP2-01-scaffold.md`).
- Código no repo-alvo: `uai-ooh-intel` @ `feat/ooh-ep2-01`.
- Próximo: **EP2-02** (domínio + portas) — destravado por esta base hexagonal. Pendência operacional
  carregada pra **EP2-09**: padronizar Java 21 + wrapper Maven e fazer smoke do health contra o VPS.
