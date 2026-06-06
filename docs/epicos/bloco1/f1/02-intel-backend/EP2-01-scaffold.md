# EP2-01 — intel: scaffold + conexão ao serving

> Bloco 1 (uAI-OOH F1) · **Épico EP2** (intel) · card 1 do kanban.
> **Depende de:** T0b (`uai-ooh-service-template`) + T18 (serving no VPS — feito) · **Paralelizável:** não (base do EP2, destrava 2–9)
> **Repo-alvo:** `uai-ooh-intel` (gerado do template) · **cwd:** `~/IdeaProjects/personal/uai/uai-ooh-intel` · **Stack:** Java 21 / Spring Boot
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §6; `arquitetura-servicos.md` (intel `:8085`, `ooh_intel_ro`, ADR-003/004).

## Objetivo
Bootar o serviço `uai-ooh-intel` a partir do `uai-ooh-service-template` (hexagonal single-module), conectado ao banco `ooh` via usuário **read-only `ooh_intel_ro`** (SELECT só em `serving`), lendo por **`dataset_version` ACTIVE**, com health. Base pronta pra receber domínio/endpoints (tarefas 2+). **Sem PostGIS/`ST_*` no runtime** (ADR-003) e **sem tenant** (ADR-004).

## Como executar
0. **Criar o repo:** `gh repo create mddinizbh/uai-ooh-intel --private` + push de uma **`main` vazia** (commit baseline) **antes de qualquer código** (token `create-intel` na orquestração).
1. **Regerar LIMPO do template:** se o repo já tiver um scaffold antigo/parcial, **remover o conteúdo e regerar do `uai-ooh-service-template` atual** (baseline limpo e atualizado com a evolução do template — **não** evoluir sobre scaffold velho). Depois renomear pacote base (`com.uai.ooh.intel`), `artifactId`/Docker, porta **`:8085`**. ⟶ **Convenção: vale pra TODO repo gerado do template** (EP3/EP4 e futuros).
2. **Datasource** Spring → `ooh` via `ooh_intel_ro` (schema default `serving`, HikariCP, read-only). Profiles `local` e `prod`. **Sem Flyway/DDL** — o `serving` é do normalizer; o intel só lê.
3. **`ActiveVersionResolver`**: resolve `serving.dataset_version WHERE status='ACTIVE'` (cache curto); toda leitura filtra por esse `version_id`.
4. **Health**: `HealthIndicator` custom — checa conexão ao `ooh` + existência de `dataset_version` ACTIVE → `/actuator/health`.
5. Boot **loga** o `version_id` ACTIVE resolvido.

## Decisão
- **Alvo de dev = serving LOCAL** (decidido 2026-06-05): ITs com Testcontainers + pg local no dev; não acopla ao VPS. **Prod** aponta pro `ooh` do VPS via profile.

## Detalhe técnico (p/ o coder)
**Estrutura (hexagonal single-module):**
```
com.uai.ooh.intel
 ├─ domain/model            # records (EP2-02)
 ├─ application/port        # in/out (EP2-02)
 ├─ adapter/in/web          # controllers (EP2-03+)
 ├─ adapter/out/persistence # JdbcTemplate readers (EP2-03+)
 └─ config                  # datasource, security, version resolver, health
```
**Config (`application.yml`):**
- profiles `local` (pg local; Testcontainers nos ITs) e `prod` (ooh do VPS).
- datasource: usuário `ooh_intel_ro`, `currentSchema=serving`, HikariCP `read-only: true`, **pool pequeno**.
- secrets via env (não commitar): `OOH_DB_URL`, `OOH_INTEL_RO_USER`, `OOH_INTEL_RO_PASS` — injetadas pelo `uai-infra` no prod.
- **sem Flyway** (o intel não tem DDL próprio no F1).

**`ActiveVersionResolver`:**
- query: `SELECT version_id FROM serving.dataset_version WHERE status='ACTIVE'` (espera **exatamente 1**; hoje = `version_id=4`).
- **cache curto** (TTL ~30–60s ou refresh agendado); **resolve 1× por request** → consistência durante um swap atômico.
- edge cases: **0 ACTIVE → health DOWN** (não serve dado inconsistente); **>1 ACTIVE → erro** (invariante violado).

**Health (`/actuator/health`):**
- indicator `datasetVersion`: UP se há 1 ACTIVE (expõe o `version_id`); DOWN se 0.
- + connectivity do datasource (indicator default do Spring).

**Nota de dependência (não bloqueia o scaffold):** o **serving base** (`line`, `line_metrics`, `line_shape`, `line_stop`, `line_profile_demografico`, `dataset_version`) já existe → o scaffold conecta e fica verde. As tabelas `line_area` (T8b), `line_corridor`/`line_poi` (EP1-02) **só são exigidas pelos endpoints que as usam** (EP2-06 / EP2-08), não pelo boot.

## Critério de pronto (verificável)
- `uai-ooh-intel` sobe e conecta no `ooh` via `ooh_intel_ro`.
- `/actuator/health` verde (inclui o indicator de `dataset_version` ACTIVE).
- `version_id` ACTIVE resolvido e logado no boot.
- Build compila; **sem PostGIS no runtime**; pacote hexagonal pronto pra receber o domínio (tarefa 2).

## Produz
- docs/epicos/runs/EP2-01-scaffold.md
