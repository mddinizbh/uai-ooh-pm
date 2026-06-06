# F1 — Handoff de estado (espelho)

> Espelho legível de `state.json`. Fonte de verdade = banco `ooh` + repos.
> Atualizado por: **back** · em **2026-06-06T11:09:29Z**

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
| `module` | (sem entradas) | — |

> `contract` e `decisions` seguem vazios neste handoff.

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

## Pendências / bloqueios

- ⏸️ **EP3-03 (lane shell → paused)**: pausada por **deploy**. Retomar quando o deploy destravar.
- 🟠 **EP2-07 (lane back → partial)**: auth em modo **stub** — depende do bootstrap do `uai-auth` (gate RED) para SSO/introspecção RFC 7662 real.
- 🔴 **uai-auth (bloqueante p/ SSO real)**: repo vazio (bootstrap pelo `epic-002-uai-auth-minimo`); até lá EP3-02/EP2-07 seguem em modo **stub** (sem RFC 7662 real).
- 🟡 **repos (não-bloqueante)**: scaffold do F1 — `uai-ooh-intel` criado por EP2-01 (lane back **complete**); EP3-01 (forka `uai-portal` do `uai-spark`) já **done**.

> Lane `back` fechada como **complete**: EP2-08 (camadas GeoJSON) e EP2-09 (testes+deploy) concluídas; EP2-07 (auth) entregue parcial em modo stub.
