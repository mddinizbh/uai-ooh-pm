# EP2-03 — intel: catálogo + ficha (endpoints de leitura)

> Bloco 1 (uAI-OOH F1) · **Épico EP2** (intel) · card 3 do kanban.
> **Depende de:** EP2-02 (domínio+portas) · **Destrava (front):** EP4-02 (lista+filtros), EP4-03 (ficha), EP4-05 (charts) · **Paralelizável:** parcial (com EP2-04)
> **Repo-alvo:** `uai-ooh-intel` · **Stack:** Java 21 / Spring Boot
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §6; `epico-4-serving-app.md` (4.4).

## Objetivo
Implementar os endpoints de **leitura** do catálogo e da ficha sobre o `serving` (`dataset_version` ACTIVE): adapter/out (persistência), adapter/in (web), mappers, OpenAPI. **Sem `ST_*`/PostGIS no runtime** (ADR-003).

## Endpoints
| Verbo · path | Retorno | Fonte (serving) |
|---|---|---|
| `GET /api/lines` | `List<Line>` (id, shortName, longName, scoreTotal) | `line` + `line_metrics.score_total` |
| `GET /api/lines/{id}` | `LineDetail` (catálogo + shapes GeoJSON + pontos) | `line` + `line_shape(geom_geojson)` + `line_stop` |
| `GET /api/lines/{id}/metrics` | `LineMetrics` (alcance, perfil, demanda, POIs/categoria, arterial, impressões, 5 sub-scores) | `line_metrics` (+ `line_profile_demografico`) |

- **404** p/ linha inexistente. **OpenAPI/Swagger** publicado.

## Decisão
- **Persistência: JdbcTemplate** (decidido 2026-06-05 — SQL explícito p/ o filtro por `version` ACTIVE e o `jsonb` GeoJSON passando direto). Sem JPA/Hibernate. *(Se o `uai-ooh-service-template` já trouxer outro stack, alinhar — mas o alvo é JdbcTemplate.)* Vale como padrão de leitura p/ EP2-04/05/06 também.

## Como executar
1. **adapter/out** — impl de `NetworkQueryRepository`: lê `serving` filtrando por `dataset_version` ACTIVE (`ActiveVersionResolver` do EP2-01); mappers row→record; `geom_geojson` (jsonb) passa direto como JSON no payload.
2. **adapter/in** — controllers REST dos 3 endpoints; 404 p/ id inexistente; OpenAPI (springdoc, `/swagger-ui`).
3. Sem `ST_*`/PostGIS no runtime; sem JPA/Hibernate.

## Critério de pronto (verificável)
- Os 3 endpoints respondem do `serving` (version ACTIVE), sem `ST_*`.
- `GET /api/lines` devolve as **303** linhas com score; `/{id}` devolve shapes GeoJSON + pontos; `/metrics` devolve o `LineMetrics` completo.
- 404 em id inexistente; OpenAPI publicado.
- (ITs completos ficam no EP2-08; aqui smoke test dos 3 endpoints.)

## Produz
- docs/epicos/runs/EP2-03-catalogo-ficha.md
