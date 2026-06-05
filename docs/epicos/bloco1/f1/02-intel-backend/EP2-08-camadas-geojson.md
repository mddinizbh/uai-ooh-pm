# EP2-08 — intel: camadas GeoJSON do corredor (exposição)

> Bloco 1 (uAI-OOH F1) · **Épico EP2** (intel) · card 8 do kanban.
> **Depende de:** EP2-03 (leitura/JdbcTemplate) + **EP1-02** (materialização das camadas no serving) · **Pré-req de:** EP4-04 (mapa) · **Paralelizável:** parcial
> **Repo-alvo:** `uai-ooh-intel` · **Stack:** Java 21 / Spring Boot
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §5.3. *(Era a metade-intel do antigo EP2-09; a materialização virou `EP1-02` no pipeline — single-repo.)*

## Objetivo
**Expor** as camadas geográficas do corredor em **GeoJSON**, lendo as tabelas **já materializadas no serving** (EP1-02). **Sem `ST_*` em runtime** (ADR-003) — só SELECT plano + `jsonb` passthrough.

## Endpoint
- `GET /api/lines/{id}/geo` → FeatureCollection com **trajeto + corredor 300m + pontos + POIs por categoria** (GeoJSON).
  - ← `serving.line_shape` (trajeto) + `serving.line_corridor` (buffer) + `serving.line_stop` (pontos) + `serving.line_poi` (POIs geom+categoria), todos pela `dataset_version` ACTIVE.

## Critério de pronto (verificável)
- `GET /api/lines/{id}/geo` devolve GeoJSON válido (trajeto + corredor + pontos + POIs por categoria) p/ uma linha, da version ACTIVE.
- **Sem `ST_*`** em runtime.
- Front (EP4-04) consegue **togglar POIs por categoria** com esse payload.

## Produz
- docs/epicos/runs/EP2-08-camadas-geojson.md
