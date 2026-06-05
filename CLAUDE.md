# uai-ooh-pm — CLAUDE.md

> **Papel deste repo:** hub de **planejamento & tracking** do vertical uAI-OOH (inteligência e
> metrificação de mídia OOH em ônibus de BH). Aqui vivem épicos, tasks, ADRs e apontamentos de
> execução (`runs/`). **Não há código de aplicação aqui** — o código dos serviços vive nos repos
> próprios (`uai-ooh-pipeline`, `uai-ooh-intel`, `uai-ooh-web`, `uai-infra`). Ver `README.md`.

## O que fazer / não fazer neste repo

- **Trabalho aqui = documentos**: mapa de execução do F1 em `docs/epicos/bloco1/f1/` (lanes
  dados/back/front, cada task com doc + decisões), apontamentos de execução (`docs/epicos/runs/`),
  PRDs (`docs/prd/`) e ADRs. Mapa antigo (T0–T20b) arquivado em `docs/epicos/bloco1/_arquivo/`. Nada de build/test de código.
- **Validação de execução** é feita contra o banco `ooh` (via MCP `postgres-ooh`) e registrada no `run`
  correspondente — counts reais vs. esperado, decisões, desvios, estado do `dataset_version`.
- **Promoção pro vault** Obsidian via skill `/vault-update` ao fechar cada sessão (vault = canônico).
- **Código novo de OOH não nasce aqui** — vai pro repo-alvo indicado na task (campo `Repo-alvo/cwd/stack`).

## App legado `uai-bus-lines-map` — removido, congelado na tag `legacy-frozen`

O código do app legado (API Java `uai-buslines`, pacote `com.uai.buslines`, porta `8085`,
Java 21 / Spring Boot 3.3.6 + SPA React/Vite `uai-buslines-web`) **foi removido deste repo em
2026-06-04**. Ele continua **LIVE** em `linhas.uaiagencia.com.br` rodando a imagem GHCR
`ghcr.io/mddinizbh/uai-buslines:latest` (+ `-web`) **já publicada**, deployada pelo `uai-infra`
— este repo não rebuilda nem deploya nada.

- **Não evoluir o legado.** Ele será aposentado no cutover pós-F1 (quando `uai-ooh-intel` +
  `uai-ooh-web` assumem o domínio).
- **Recuperar o código** (se precisar de hotfix antes do cutover):
  ```
  git checkout legacy-frozen -- src web pom.xml Dockerfile .dockerignore
  ```
  Decisões arquiteturais do legado que importam ao recuperar: **sem `tenant_id`** (ADR-004, tool
  civic público single-tenant) e **sem PostGIS** — geometria como `jsonb` GeoJSON, classificação
  de bairro precomputada com JTS no import GTFS (ADR-003).

## Convenção de tracking

- Cada task executada (em qualquer repo OOH) deixa um apontamento em `docs/epicos/runs/`.
- Tasks do Bloco 1 carregam o campo `Repo-alvo/cwd/stack` indicando onde o código é escrito.
- Estado do F1: ver `docs/epicos/bloco1/f1/README.md` (mapa por lane) + tabela em `README.md` (fonte de
  verdade = banco `ooh` schema `core` + commits dos repos).
