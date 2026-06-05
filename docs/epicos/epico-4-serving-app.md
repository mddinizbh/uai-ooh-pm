# Épico 4 — Servir ao produto (`intel` lê o schema `serving` no `ooh-postgis` + endpoints F1)

> Normalização F1, parte 4. O **`uai-ooh-intel`** (serviço NOVO, gerado do `uai-ooh-service-template` —
> o `uai-bus-lines-map` fica **congelado**) serve catálogo + ficha + ranking lendo o schema **`serving`**
> (tabelas planas, sem geometry) **no próprio banco `ooh`** (materializado pelo `normalizer`).
> **SEM export cross-DB pro `uai_buslines`** (decisão do dono 2026-06-04 — ver `verticais/ooh/decisoes.md`).
> **Depende de:** Épico 3 (`core.line_metrics`, `core.line_profile_demografico`, `core.line_stop`).
> Mantém ADR-003 (sem PostGIS no serving — `intel` só faz SELECT plano) e ADR-004 (sem `tenant_id`).

---

## Tarefa 4.1 — Schema `serving` no `ooh` (materializado pelo `normalizer`)
Tabelas PLANAS (sem geometry; números + jsonb onde precisar), no schema **`serving`** do banco `ooh`,
preenchidas pelo `normalizer` (módulo do `uai-ooh-pipeline`) ao fim do Épico 3 — **não há migração no
`uai_buslines`**. Usuário read-only `ooh_intel_ro` com `GRANT SELECT` só em `serving`. Tabelas:
- `serving.line` — catálogo (short_name, long_name, categoria, flags). O intel **reimplementa o catálogo**
  que o bus-lines servia (bus-lines não é mais o backend).
- `serving.line_shape` — shape por service_type/direction com `geom_geojson` (jsonb 4326, sem geometry typed) p/ o mapa.
- `serving.line_stop` — sequência (line_id, stop_id, direction, stop_sequence, is_terminal).
- `serving.line_metrics` — espelha `core.line_metrics` SEM `geom_*`: pop_corredor_pond, renda_media_pop,
  classe_predom, pct_pop_ab/de, n_poi_*, pct_arterial, classe_via_predom, passageiros_/impressoes_{util,sab,dom},
  faixa_indicativa_pct, score_total + 5 sub-scores, metodo_versao.
- `serving.line_profile_demografico` — (line_id, classe_renda, pop_na_classe, pct_pop).
- Versão por `serving.dataset_version` (swap atômico IMPORTING→ACTIVE).
- **Pronto:** schema `serving` populado no `ooh`; `ooh_intel_ro` lê só `serving`; nenhuma coluna geometry.

## Tarefa 4.2 — Materialização `core` → `serving` (mesmo banco, sem cross-DB)
- O `normalizer` materializa `core.*` → `serving.*` **no MESMO banco `ooh`** (`INSERT…SELECT` server-side;
  sem dblink/FDW; sem geometry no destino — `geom_geojson` via `ST_AsGeoJSON`). Nova `dataset_version` em
  `IMPORTING` → flip atômico para `ACTIVE`, arquivando a anterior.
- **Validação:** contagens batem entre `core` e `serving`; swap atômico (nunca estado parcial visível).
- **Pronto:** `serving` servível; versão ativa coerente.

## Tarefa 4.3 — `uai-ooh-intel`: domínio + portas (do template, hexagonal)
- Serviço NOVO a partir do `uai-ooh-service-template`. `domain/model`: records puros `Line`/`LineDetail`
  (catálogo + shapes), `LineMetrics`, `LineImpressions`, `DemographicProfile`, sealed `ClasseRenda {A..E}`,
  `ScoreBreakdown` (5 sub-scores). Reimplementa o catálogo/ficha que o bus-lines tinha + as métricas novas.
- `port/in QueryNetworkUseCase`: `LineDetail line(long id)`, `LineMetrics lineMetrics(long id)`,
  `List<LineRanking> rankLines(ScoreWeights w)` (re-rank em memória sobre os 5 sub-scores — sem PostGIS).
- `port/out NetworkQueryRepository`: leitura plana do schema `serving`.
- **Pronto:** intel compila; portas definidas.

## Tarefa 4.4 — `intel`: persistência + web
- `adapter/out/persistence`: entities/repos/mapper lendo `serving.*` (SELECT plano por `dataset_version`
  ACTIVE, via `ooh_intel_ro`).
- `adapter/in/web`: `GET /api/lines` (catálogo), `GET /api/lines/{id}` (ficha + shapes GeoJSON),
  `GET /api/lines/{id}/metrics`, `GET /api/lines/ranking?weights=...&publicoAlvo=AB|DE` (A5 recalibra `s_perfil`). OpenAPI.
- **Pronto:** endpoints respondem do schema `serving` no `ooh-postgis` sem tocar PostGIS em runtime; Swagger atualizado.

## Tarefa 4.5 — Frontend (opcional em F1)
- A SPA React/MapLibre (hoje no `uai-bus-lines-map` congelado) passa a consumir o `intel`. Em F1, painel
  de **ficha** (alcance/renda/classe/arterial/POIs/impressões com aviso "estimativa/ranking") + tela de
  **ranking**, reaproveitando o layout de `docs/simulacao-linha-4107.html`. **Cutover** do domínio
  `linhas.uaiagencia.com.br` (SPA → intel) é **pós-F1**.
- **Pronto:** ficha visível consumindo o intel (mesma honestidade da simulação).

## Tarefa 4.6 — Testes
- ITs com Testcontainers `postgres:16` (tabelas planas, sem PostGIS): seed de `serving.*` fixo, testar
  `/metrics` e `/ranking` (inclusive re-rank por pesos e por público-alvo).
- `mvn verify` (Java 21, JaCoCo ≥80%) verde; `api.version=1.44` (Docker Desktop 29.x).
- **Pronto:** cobertura e ITs passando.

---

## Critério de pronto do Épico 4 (= F1 entregue)
- `uai-ooh-intel` (novo) serve **catálogo (A1)** + **ficha** (A2/A3) + **ranking por score/público-alvo
  (A4/A5)** lendo o schema `serving` no `ooh-postgis` (sem PostGIS em runtime), com swap atômico de versão.
- Honestidade preservada: impressões = estimativa/ranking, faixa ±35%, trânsito = exposição arterial (volume real = v2).
- `mvn verify` verde; OpenAPI atualizado; **`uai-bus-lines-map` intocado**. **F1 (inteligência de planejamento) no ar.**

> **Pós-F1 / paralelo:** v2 — coletor próprio de congestionamento; backlog — enriquecimento de contatos
> de POIs (`docs/backlog-features.md`); Blocos 2 (tempo real) e 3 (comercial); cutover SPA→intel.
