# T8b — Regionalização espacial (regional administrativa + bairro por setor/stop/linha)

> Tarefa do Bloco 1 (uAI-OOH F1). **Auto-suficiente**: a sessão que rodar isto deve ler só os apontamentos abaixo + este arquivo.
> **Épico:** Épico 2 (contexto — irmã de T8/T9/T10) · **Depende de:** T8 (census_sector) + build de `core.stop` + T5 (line/line_stop) · **Paralelizável:** parcial (independe de T9/T10/T11–T14)
> **Repo-alvo:** `uai-ooh-pipeline` (módulos ingestor + normalizer) · **cwd:** `~/IdeaProjects/personal/uai/uai-ooh-pipeline` · **Stack:** Python + PostGIS
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §12 — o filtro **região→bairro** do front F1 precisa da **regional administrativa de BH**, que **NÃO existe no core**. Verificado no banco `ooh` 2026-06-05: o `nm_regiao` do censo é a **macrorregião IBGE** (= "Sudeste" p/ 100% dos 5.167 setores — inútil). Há bairro (`census_sector.nm_bairro`, 476) + geom; `core.stop` tem `geom_31983` (GiST) mas **sem** bairro/região.

## Objetivo
Cravar a **regional administrativa (as 9 de BH)** e o **bairro** no `core` de forma **espacial** (sem name-matching, consistente com ADR-003), e derivar a **área servida por linha**:

1. **Ingestor** — baixar os **polígonos das 9 regionais administrativas da PBH** (dados abertos PBH / BHMap / geoserver; camada "Regional"/"Regionais Administrativas") → `raw.pbh__regional` (geom como TEXT WKT/GeoJSON; SRID a normalizar p/ 31983 — geom PBH no raw costuma vir SRID 0, usar `ST_SetSRID`).
2. **Normalizer (core)** — version-stamped, idempotente:
   - `core.regional` (regional_id, nome, `geom_31983`) — **9** feições, GiST.
   - **`core.census_sector` → regional**: nova coluna `regional` (sector ∩ regional; critério p/ bairro fronteiriço = maior interseção ou centroide — decidir e registrar).
   - **`core.stop` → bairro + regional**: novas colunas `nm_bairro`, `regional` (`stop.geom_31983` ∩ `census_sector` p/ bairro; ∩ `core.regional` p/ regional).
   - **`core.line_area`** (line_id, version_id, regional, bairro): conjunto **distinto** de bairros/regionais que cada linha serve, derivado de `line_stop`→`stop`.
3. Seguir as **convenções do normalizer** (ver `arquitetura-servicos.md` §Índices na raw): **GiST em temp-table** p/ geom da raw, **btree+ANALYZE** nas chaves, **`INSERT…SELECT` server-side** (não trazer geom pro Python). Tudo carimbado com `version_id` da `dataset_version` BUILDING corrente.

## Apontamentos a LER antes de começar
- /Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/prd/2026-06-05-ooh-intel-front-f1.md (§5.2 filtros, §6 endpoints, §12 dependência)
- /Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/arquitetura-servicos.md (§Índices na raw — convenção GiST temp / btree+ANALYZE / INSERT…SELECT)
- /Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/plano-normalizacao-core.md (§Schema core)
- /Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/epicos/bloco1/tarefas/T8.md (padrão do build de `census_sector` — reusar o approach espacial)

## Critério de pronto (verificável no banco `ooh` via MCP `postgres-ooh`)
- `raw.pbh__regional` = **9** feições; nomes batem as 9 regionais: **Barreiro, Centro-Sul, Leste, Nordeste, Noroeste, Norte, Oeste, Pampulha, Venda Nova**.
- `core.regional` = 9 (geom válida, SRID 31983).
- `core.census_sector.regional` preenchida p/ 100% dos 5.166 setores (0 nulos onde há geom); cada bairro → exatamente 1 regional (critério de fronteiriço registrado).
- `core.stop` com `nm_bairro` + `regional` (0 nulos onde há geom; reportar stops fora de qualquer regional, se houver).
- `core.line_area` populada p/ as 303 linhas (cada linha ≥ 1 regional e ≥ 1 bairro).
- counts reais conferidos e registrados no run.

## Destrava (DEPOIS, fora desta task)
- **T17** (serving): projetar `line → regionais/bairros` + taxonomia região→bairro no `serving`.
- **T19** (intel): `GET /api/regions` + filtros `regiao`/`bairro` em `/api/lines` e `/api/lines/ranking`.
- **T20b** (web): cascata região→bairro nos filtros.

## Apontamentos a PRODUZIR ao terminar
- docs/epicos/runs/T8b-regionalizacao.md (fonte PBH usada + URL, SRID, contagens reais setor/stop/linha, critério de bairro-fronteiriço, estado do `dataset_version`).
