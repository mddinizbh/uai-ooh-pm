# 01 · Pipeline de dados — 🟫 DATA

> Repo: **`uai-ooh-pipeline`** (Python + PostGIS) · materializações **novas** no `serving` que o F1 precisa,
> além da base já entregue (T1–T18, em `../../_arquivo/`).

| Task | O que entrega | Estado |
|---|---|---|
| [T8b](T8b.md) | **Regionalização espacial** — `serving.line_area` (regional+bairro por linha), via polígonos PBH | ✅ **done** (validado no banco) |
| [EP1-02](EP1-02-camadas-corredor-serving.md) | **Camadas do corredor** — `serving.line_corridor` (buffer 300m) + `serving.line_poi` (POIs geom+categoria) | ✅ **done** (303/303, validado) |
| [EP1-03](EP1-03-pontos-serving.md) | **Pontos das paradas** — `serving.line_stop.geom_geojson` (Point 4326) | a fazer (gap do EP2-08) |

**Destrava:** T8b → filtros (EP2-06) · EP1-02 → corredor/POI no mapa (EP2-08) · **EP1-03 → pontos no mapa (EP2-08, 4ª camada → EP4-04)**.
**Validação:** contra o banco `ooh` via MCP `postgres-ooh` (counts reais), registrada em `docs/epicos/runs/`.
