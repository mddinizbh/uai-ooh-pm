# 01 · Pipeline de dados — 🟫 DATA

> Repo: **`uai-ooh-pipeline`** (Python + PostGIS) · materializações **novas** no `serving` que o F1 precisa,
> além da base já entregue (T1–T18, em `../../_arquivo/`).

| Task | O que entrega | Estado |
|---|---|---|
| [T8b](T8b.md) | **Regionalização espacial** — `serving.line_area` (regional+bairro por linha), via polígonos PBH | 🏃 **em execução** |
| [EP1-02](EP1-02-camadas-corredor-serving.md) | **Camadas do corredor** — `serving.line_corridor` (buffer 300m) + `serving.line_poi` (POIs geom+categoria) | a fazer |

**Destrava:** T8b → filtros (EP2-06) · EP1-02 → mapa (EP2-08 expõe, EP4-04 renderiza).
**Validação:** contra o banco `ooh` via MCP `postgres-ooh` (counts reais), registrada em `docs/epicos/runs/`.
