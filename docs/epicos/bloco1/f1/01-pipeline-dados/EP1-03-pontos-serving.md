# EP1-03 — Pontos das paradas no serving (geometria GeoJSON)

> Bloco 1 (uAI-OOH F1) · Épico EP1 (dados) · **NOVO** — gap achado pela **validação adversarial do EP2-08** (2026-06-06).
> **Depende de:** T17 (`serving.line_stop` flat) + `core.stop` (geom_31983) · **Alimenta:** EP2-08 (4ª camada do `/geo`) → EP4-04 (marcadores de parada no mapa)
> **Repo-alvo:** `uai-ooh-pipeline` · **Stack:** Python + PostGIS · **Paralelizável:** sim (independe de outras)

## Por que existe (origem)
O `/geo` (EP2-08) deve servir **4 camadas**: trajeto + corredor + **pontos (paradas)** + POIs. Mas `serving.line_stop`
foi materializado **sem geometria** (só `line_id, service_type, direction, stop_sequence, stop_id, is_terminal, version_id`).
A geometria das paradas só existe em `core.stop.geom_31983` (PostGIS, SRID 31983). Servir os pontos **sem `ST_*` em runtime**
(ADR-003) exige **precomputar a geometria como GeoJSON no serving** — feito uma vez no pipeline. Sem isso o mapa do front
sai **sem marcadores de parada**.

## Objetivo
Materializar `serving.line_stop.geom_geojson` (**Point**, GeoJSON SRID **4326**) por parada, **version-stamped** e
**idempotente**, no mesmo padrão do EP1-02: `ST_AsGeoJSON(ST_Transform(geom_31983, 4326))` no pipeline; o serving lê `jsonb` plano.

## Tarefas
1. **DDL** — adicionar coluna `geom_geojson` (jsonb) em `serving.line_stop` (version-stamped, idempotente — `ADD COLUMN IF NOT EXISTS`).
2. **Materialize** — popular `geom_geojson` via `core.stop.geom_31983` → `ST_Transform(…, 4326)` → `ST_AsGeoJSON(…)::jsonb`,
   join por `stop_id`, server-side `UPDATE … FROM` (ou `INSERT…SELECT` na rematerialização), carimbado com o `version_id` corrente.
3. **Validate** — `0` null onde `core.stop.geom_31983` existe; tipo `Point`; sem `crs` no GeoJSON (4326 implícito).

## Critério de pronto
- `serving.line_stop.geom_geojson` populado (Point GeoJSON 4326) para as paradas das **303** linhas (version ACTIVE).
- `0` null onde `core.stop` tem geometria; `0` com tipo ≠ Point; `0` com `crs`.
- counts conferidos no banco `ooh` e registrados no run.

## Convenções
GiST/btree onde fizer sentido · `UPDATE…FROM`/`INSERT…SELECT` server-side · idempotente · `version_id` corrente · **sem `ST_*` no serving em runtime** (só no pipeline).

## Produz
- `docs/epicos/runs/EP1-03-pontos-serving.md`
