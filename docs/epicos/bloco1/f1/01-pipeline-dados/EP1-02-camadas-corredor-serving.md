# EP1-02 — pipeline: materializar camadas do corredor no serving (buffer 300m + POIs)

> Bloco 1 (uAI-OOH F1) · **Épico EP1** (dados/serving) · **2º card do EP1** (1º = regionalização, `T8b.md`).
> **Depende de:** T11 (corredor por linha) + T12 (POIs no corredor por categoria) + T17 (serving) · **Pré-req de:** EP2-08 (exposição GeoJSON) → EP4-04 (mapa) · **Paralelizável:** parcial (com T8b)
> **Repo-alvo:** `uai-ooh-pipeline` (normalizer) · **cwd:** `~/IdeaProjects/personal/uai/uai-ooh-pipeline` · **Stack:** Python + PostGIS
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §5.3 — revelada ao detalhar o mapa (EP4-04). Separada da exposição no intel (antigo EP2-09) por ser **single-repo pipeline**.

## Objetivo
Projetar **core → serving** as camadas geográficas do corredor, **precomputadas** (pra o intel ler sem `ST_*` no runtime — ADR-003):
- `serving.line_corridor` — **buffer 300m** do trajeto como `geom_geojson` (1 por linha), do `ST_Buffer` já usado no corredor (T11).
- `serving.line_poi` — **POIs dentro do corredor** (geom ponto em GeoJSON + categoria), os já filtrados no T12.
- **version-stamped** (`dataset_version` BUILDING corrente), `INSERT…SELECT` server-side, idempotente.

## Como executar
- No normalizer: persistir o buffer 300m (reusar o `ST_Buffer` do shape do T11) como `geom_geojson` via `ST_AsGeoJSON`; e a lista de POIs do corredor com categoria (reusar o filtro espacial do T12) → tabelas flat no `serving`.
- Convenções do normalizer (GiST temp se precisar, `INSERT…SELECT` server-side).

## Critério de pronto (verificável no banco `ooh` via MCP)
- `serving.line_corridor` e `serving.line_poi` populados p/ as **303** linhas.
- Counts de POI por categoria **batem** com `line_metrics.n_poi_*`.
- `geom_geojson` válido (GeoJSON, 4326).

## Produz
- docs/epicos/runs/EP1-02-camadas-corredor.md
