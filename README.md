# uai-ooh-pm — Hub de planejamento & tracking do vertical OOH

> **Papel deste repo:** centro de **gestão de tarefas, planejamento e validação de execução** do vertical
> uAI-OOH (inteligência e metrificação de mídia em ônibus de BH). Os épicos, tasks, ADRs e apontamentos
> de execução vivem aqui; o **código** dos serviços vive nos repos próprios (ver mapa abaixo).
>
> ⚠️ **App legado removido (congelado na tag `legacy-frozen`):** o código do `uai-bus-lines-map`
> (API Java `uai-buslines` + SPA `uai-buslines-web`) **foi removido deste repo** — agora é 100% PM.
> O serviço segue **LIVE** em `linhas.uaiagencia.com.br` rodando a imagem GHCR `:latest` já publicada
> (deploy pelo `uai-infra`), até o cutover pós-F1 quando `uai-ooh-intel` + `uai-ooh-web` assumem o domínio.
> Para recuperar o código: `git checkout legacy-frozen -- src web pom.xml Dockerfile .dockerignore`.

## Mapa de repos do vertical OOH

| Repo | Stack | Papel |
|---|---|---|
| **uai-ooh-pm** (este) | docs | Planejamento, tasks, ADRs, tracking de execução |
| `uai-ooh-pipeline` | Python + PostGIS | Ingestor (raw) + normalizer (raw→core→serving) |
| `uai-ooh-service-template` | Java | Template hexagonal pros serviços Java do vertical |
| `uai-ooh-intel` *(a criar)* | Java/Spring | API de serving F1 (catálogo + ficha + ranking) |
| `uai-ooh-web` *(a criar)* | React/MapLibre | SPA de ficha + ranking |
| `uai-infra` | Compose/VPS | Deploy centralizado (inclui `ooh-postgis`) |

## Estado do F1 (planejamento) — atualizado 2026-06-04

Fonte de verdade: banco `ooh` (schema `core`) + commits dos repos. Detalhe e ordem em
[`docs/epicos/bloco1/plano-execucao.md`](docs/epicos/bloco1/plano-execucao.md).

| Camada / Épico | Tasks | Repo | Status |
|---|---|---|---|
| Fundação | T0a, T0b | pipeline / template | ✅ feito |
| Épico 0 — ingestão raw | — | pipeline | ✅ raw populado |
| Épico 1 — identidades | T1–T7 | pipeline | ✅ `core` materializado (line 304, stop 9.650, trip_pattern, line_shape, line_stop, vehicle) |
| Épico 2 — contexto | T8, T9, T10 | pipeline | 🟡 em execução (census/poi/road → core) |
| Épico 3 — métricas + score | T11–T16 | pipeline | 🔴 a fazer |
| Épico 3 — materializar serving | T17 | pipeline | 🔴 a fazer (schema `serving` ainda não existe) |
| Infra — `ooh-postgis` no VPS | T18 | infra | 🔴 a fazer |
| Épico 4 — API `intel` | T19, T20a | intel | 🔴 a fazer (repo a criar) |
| Épico 4 — SPA | T20b | web | 🔴 a fazer (repo a criar) |

## Convenção de tracking

- **Cada task executada** (em qualquer repo OOH) deixa um apontamento em [`docs/epicos/runs/`](docs/epicos/runs/)
  (counts reais vs esperado, decisões, desvios, estado do `dataset_version`).
- **Validação** das execuções é feita contra o banco `ooh` (via MCP `postgres-ooh`) e registrada no `run`.
- **Promoção pro vault** Obsidian via skill `/vault-update` ao fechar cada sessão (vault = canônico).

## Documentos-chave

- [`docs/arquitetura-servicos.md`](docs/arquitetura-servicos.md) — decomposição em microsserviços (F1→F3)
- [`docs/plano-normalizacao-core.md`](docs/plano-normalizacao-core.md) — desenho do schema `core`
- [`docs/epicos/`](docs/epicos/) — épicos 0–6 (F1: 0–4 · F2: 5 · F3: 6)
- [`docs/proxies-e-premissas.md`](docs/proxies-e-premissas.md) — honestidade do modelo de alcance/score
