# F1 — Handoff de estado (espelho)

> Espelho legível de `state.json`. Fonte de verdade = banco `ooh` + repos.
> Atualizado por: **shell** · em **2026-06-06T01:22:10Z**

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
| `data` | **complete** | `T8b` = done · `EP1-02` = done |
| `back` | (sem entradas) | — |
| `shell` | **partial** | `EP3-01` = done · `EP3-02` = partial · `EP3-03` = paused · auth_mode = **stub** |
| `module` | (sem entradas) | — |

> `contract` e `decisions` seguem vazios neste handoff.

## Run docs

- `docs/epicos/runs/T8b-regionalizacao.md`
- `docs/epicos/runs/EP1-02-camadas-corredor.md`
- `docs/epicos/runs/EP3-01-shell-nav.md`
- `docs/epicos/runs/EP3-02-login-sso.md`

## Pendências / bloqueios

- ⏸️ **EP3-03 (lane shell → paused)**: pausada por **deploy**. Retomar quando o deploy destravar.
- 🔴 **uai-auth (bloqueante p/ SSO real)**: repo vazio (bootstrap pelo `epic-002-uai-auth-minimo`); até lá EP3-02 segue em modo **stub** (sem RFC 7662 real).
- 🟡 **repos (não-bloqueante)**: scaffold do F1 ainda parcial — EP2-01 cria `uai-ooh-intel`; EP3-01 (forka `uai-portal` do `uai-spark`) já **done**.
