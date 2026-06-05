# EP2-09 — intel: testes (ITs) + deploy

> Bloco 1 (uAI-OOH F1) · **Épico EP2** (intel) · card 9 do kanban — **fecha o EP2** (closer; reconcilia o antigo T20a + deploy).
> **Depende de:** EP2-03/04/05/06/07/08 (todos os endpoints) · **Paralelizável:** não · **Repo-alvo:** `uai-ooh-intel` · **Stack:** Java 21 / Spring Boot + `uai-infra`
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §6; `epico-4-serving-app.md` (4.6); `T20a`.

## Objetivo
Cobrir o intel com **ITs** (Testcontainers, serving flat sem PostGIS) e fechar a cobertura F1; **containerizar e deployar** via GHCR + `uai-infra`, **sem cutover de nginx** (o front é pós-F1).

## Testes
- **Testcontainers `postgres:16`** (plain, **SEM PostGIS** — serving é flat, `jsonb` GeoJSON, sem `ST_*` no read path).
- **Seed fixo** de `serving.*`: `line`, `line_metrics`, `line_shape`, `line_stop`, `line_profile_demografico`, `line_corridor`, `line_poi`, **`line_area`**, `dataset_version` ACTIVE.
- **Cobrir:** `/api/lines` · `/{id}` · `/metrics` · `/ranking` (re-rank por pesos + público AB/DE) · `/aggregate` · `/regions` + filtros `regiao`/`bairro` · **`/{id}/geo`** (camadas GeoJSON) · **401** (sem token) · **404**; honestidade (impressões marcadas como estimativa).
- **JaCoCo ≥80%**; `mvn verify` verde (Java 21; `api.version` conforme template/T20a).
- ✅ Os ITs **seedam o próprio `serving`** (inclui `line_area`, `line_corridor`, `line_poi`) → **não dependem do T8b/EP1-02 rodar** (o gate do EP1 vale pro dado de produção, não pros testes).

## Deploy
- Dockerfile + build → **GHCR** (`ghcr.io/mddinizbh/uai-ooh-intel`).
- Entrada no **`uai-infra`** compose (serving no VPS já existe — T18 feito).
- Deploy só por **commit → GitHub Actions → `uai-infra`**; **nunca tocar o VPS direto**.

## Decisão
- **Deploy rodando, SEM tocar o nginx** (decidido 2026-06-05): intel sobe no VPS interno; **não** faz cutover de domínio (front pós-F1). Alinha com a nota do T18 ("ooh-intel ainda não toca o nginx").

## Critério de pronto (verificável)
- ITs verdes cobrindo **todos** os endpoints F1 (incl re-rank/público/aggregate/filtros/**geo**, 401/404); JaCoCo ≥80%; `mvn verify` verde.
- Imagem no GHCR; entrada no `uai-infra`; intel sobe no VPS via Actions; **nginx intocado**.

## Produz
- docs/epicos/runs/EP2-09-testes-deploy.md
