# F1 — Handoff de estado (espelho)

> Espelho legível de `state.json`. Fonte de verdade = banco `ooh` + repos.
> Atualizado por: **contract** · em **2026-06-06T14:53:03Z**

## Gates

| Gate | Status | Resumo |
|------|--------|--------|
| `T8b` | 🟢 GREEN | 4/4 checks batem exato (regional=9, line_area distinct=303, stops geocodificados sem bairro/regional=0, census_sector sem regional=0). Mergeado e consistente no banco `ooh` schema `core`. |
| `EP1-02` | 🟢 GREEN | `serving.line_corridor` e `serving.line_poi` materializados (303 linhas cada, 0 geom nula). dataset_version=5/ACTIVE. Destrava EP2-08. |
| `dataset_version` | 🟢 GREEN | 1 `dataset_version` ACTIVE; anterior ARCHIVED. ≥1 ACTIVE → intel serve, health verde sem depender de promoção. |
| `repos` | 🟡 YELLOW | Estado pré-scaffold esperado: `uai-ooh-intel` ausente (EP2-01 cria), `uai-portal` vazio só .idea+CLAUDE.md (EP3-01 forka), `uai-spark` 101 arquivos (fonte do fork), `uai-ooh-service-template` com pom.xml, 36 arquivos (base scaffold). Nada bloqueia. |
| `uai-auth` | 🔴 RED | Repo só-bootstrap: 0 arquivos reais, sem pom.xml/package.json, sem endpoint de introspection, sem commits. EP3-02/EP2-07 só rodam em modo stub (sem RFC 7662 real) até bootstrap do epic-002. |

## Status por lane

| Lane | Estado | Tasks |
|------|--------|-------|
| `data` | **complete** | `T8b` = done · `EP1-02` = done · `EP1-03` = done |
| `back` | **complete** | `EP2-01`..`EP2-06` = done · `EP2-07` = **partial** · `EP2-08` = done · `EP2-09` = done |
| `shell` | **partial** | `EP3-01` = done · `EP3-02` = partial · `EP3-03` = paused · auth_mode = **stub** |
| `module` | **complete** | `EP4-01`..`EP4-09` = done (EP4-01..06, EP4-09, EP4-07, EP4-08) |

### Contract-check (back ↔ front)

- **Verdict**: ✅ **aligned** — fonte do contrato: `derived-from-controllers`. Os 8 hooks da lane module batem 1:1 com os 7 endpoints F1 (`useLineGeo` e `useLinesGeo` compartilham `GET /api/lines/{id}/geo`; `useLineDetail` = `useLine` do handoff).
- **Matched (8 hooks)**: `useLines` → `GET /api/lines` · `useRegions` → `GET /api/regions` · `useLineDetail` → `GET /api/lines/{id}` · `useLineMetrics` → `GET /api/lines/{id}/metrics` · `useRanking` → `GET /api/lines/ranking` · `useAggregate` → `POST /api/lines/aggregate` · `useLineGeo` → `GET /api/lines/{id}/geo` · `useLinesGeo` → batch do mesmo `/geo` (reusa cache).
- ⚠️ **1 mismatch (major, não-crítico)** — `missing-endpoint`: o binário em `localhost:8085` é build **stale** do template. `/api/docs` (springdoc) expõe só `GET /api/ping` e `GET /api/lines` retorna 404. springdoc varre `@RestController` no startup → ausência confirma jar antigo, anterior aos controllers F1. É **staleness de deploy, não divergência de contrato**: rebuild/redeploy resolve sem tocar no contrato. Por isso `major`, não `critical`.

### Back — contrato exposto (para a lane front/module)

- **OpenAPI**: `/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-intel` (`/v3/api-docs` em runtime ou `openapi.json` do build)
- **Endpoints**:
  - `GET /api/lines`
  - `GET /api/lines/{id}`
  - `GET /api/lines/{id}/metrics`
  - `GET /api/lines/ranking`
  - `POST /api/lines/aggregate`
  - `GET /api/regions`
  - `GET /api/lines/{id}/geo`

### Module — hooks de dados (lane front)

`useLines` · `useLine` · `useLineMetrics` · `useRanking` · `useAggregate` · `useRegions` · `useLineGeo`

## Run docs

- `docs/epicos/runs/T8b-regionalizacao.md`
- `docs/epicos/runs/EP1-02-camadas-corredor.md`
- `docs/epicos/runs/EP3-01-shell-nav.md`
- `docs/epicos/runs/EP3-02-login-sso.md`
- `docs/epicos/runs/EP2-01-scaffold.md`
- `docs/epicos/runs/EP2-02-dominio-portas.md`
- `docs/epicos/runs/EP2-03-catalogo-ficha.md`
- `docs/epicos/runs/EP2-07-auth.md`
- `docs/epicos/runs/EP2-04-ranking.md`
- `docs/epicos/runs/EP2-05-agregacao.md`
- `docs/epicos/runs/EP2-06-regioes-filtros.md`
- `docs/epicos/runs/EP2-08-camadas-geojson.md`
- `docs/epicos/runs/EP1-03-pontos-serving.md`
- `docs/epicos/runs/EP2-09-testes-deploy.md`
- `docs/epicos/runs/EP4-01-scaffold-modulo.md`
- `docs/epicos/runs/EP4-02-lista-filtros.md`
- `docs/epicos/runs/EP4-03-ficha.md`
- `docs/epicos/runs/EP4-04-mapa.md`
- `docs/epicos/runs/EP4-05-charts.md`
- `docs/epicos/runs/EP4-06-cesta.md`
- `docs/epicos/runs/EP4-09-mapa-cesta.md`
- `docs/epicos/runs/EP4-07-export-pdf.md`
- `docs/epicos/runs/EP4-08-honestidade.md`

## Pendências / bloqueios

- ⏸️ **EP3-03 (lane shell → paused)**: pausada por **deploy**. Retomar quando o deploy destravar.
- 🟠 **EP2-07 (lane back → partial)**: auth em modo **stub** — depende do bootstrap do `uai-auth` (gate RED) para SSO/introspecção RFC 7662 real.
- 🔴 **uai-auth (bloqueante p/ SSO real)**: repo vazio (bootstrap pelo `epic-002-uai-auth-minimo`); até lá EP3-02/EP2-07 seguem em modo **stub** (sem RFC 7662 real).
- 🟡 **repos (não-bloqueante)**: scaffold do F1 — `uai-ooh-intel` criado por EP2-01 (lane back **complete**); EP3-01 (forka `uai-portal` do `uai-spark`) já **done**.
- 🟠 **deploy stale do intel (não-bloqueante p/ contrato)**: binário em `localhost:8085` é build antigo do template (`/api/docs` só `GET /api/ping`, `/api/lines` → 404). Rebuild/redeploy do `uai-ooh-intel` resolve — contrato em si está **aligned**.

> Lane `module` fechada como **complete**: EP4-01..06 + EP4-09 + EP4-07 + EP4-08 done; 7 hooks de dados ligados ao contrato back.
