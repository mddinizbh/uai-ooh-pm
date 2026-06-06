# Run — EP1-02 (camadas do corredor no serving: buffer 300m + POIs) · 2026-06-05 · ✅ DONE

> Execução no `uai-ooh-pipeline` (branch `feat/ooh-ep1-02`), normalizer projetando **core → serving**
> as camadas geográficas precomputadas do corredor (ADR-003: intel lê sem `ST_*` no runtime).
> Estado **verificado no banco `ooh`** via MCP `postgres-ooh` em 2026-06-05, contra a versão de serving
> **ACTIVE (v5)**. Task do mapa F1: `docs/epicos/bloco1/f1/01-pipeline-dados/EP1-02-camadas-corredor-serving.md`.
>
> **Veredito:** os 3 critérios de pronto passam, reconferidos AGORA no banco. `line_corridor` e
> `line_poi` materializados para as 303 linhas, POI por categoria casando 1:1 com `line_metrics.n_poi_*`
> e GeoJSON 100% válido em WGS84. A rodada de refute **não derrubou** o done. → **DONE**.

## Counts reais vs. esperado (banco `ooh`, MCP `postgres-ooh`, serving v5 ACTIVE)

| Gate | Esperado | Medido no banco | Veredito |
|---|---|---|---|
| Tabelas no `serving` | `line_corridor` + `line_poi` criadas (DDL) | `serving` passou de 7 → 9 tabelas; `to_regclass` não-nulo em ambas | ✅ |
| `serving.line_corridor` (rows) | 303 (1 polígono por linha) | 303 rows | ✅ |
| `serving.line_corridor` (distinct `line_id`) | 303 (sem duplicata, idempotente) | 303 distinct line_id | ✅ |
| `serving.line_poi` (distinct `line_id`) | 303 linhas com POI | 303 distinct line_id | ✅ |
| `serving.line_poi` (rows) | = `Σ line_metrics.n_poi_total` | 2.168.837 rows | ✅ |
| POI `alimentacao` vs `n_poi_alimentacao` | iguais | 122.012 = 122.012 | ✅ |
| POI `comercio` vs `n_poi_comercio` | iguais | 193.599 = 193.599 | ✅ |
| POI `educacao` vs `n_poi_educacao` | iguais | 28.041 = 28.041 | ✅ |
| POI `saude` vs `n_poi_saude` | iguais | 74.618 = 74.618 | ✅ |
| `line_poi` total vs `Σ n_poi_total` | iguais | 2.168.837 = 2.168.837 | ✅ |
| `line_corridor.geom_geojson` | GeoJSON Polygon, 4326 | 303 `Polygon`; 0 não-polígono; 0 com campo `crs` (⇒ 4326) | ✅ |
| `line_poi.geom_geojson` | GeoJSON Point, 4326 | 0 null; 0 não-`Point`; 0 com campo `crs` (⇒ 4326) | ✅ |
| Grants `ooh_intel_ro` | SELECT no `serving`, negado em `core`/`raw` | `serving.line_corridor`/`line_poi` = SELECT; `core.line_metrics` = negado | ✅ |
| `serving.dataset_version` | exatamente 1 ACTIVE | v5 ACTIVE + v4 ARCHIVED = 1 ACTIVE | ✅ |

## Build / testes (escopo declarado)

- **Compilação:** `py_compile` em todos os `.py` rastreados (`git ls-files`) = `PY_COMPILE_OK`; o teste
  novo *untracked* `tests/test_serving_ep1_02.py` compilado à parte = `TEST_COMPILE_OK`.
- **Suite:** `.venv/bin/python -m pytest -q` (fallback do repo — o `python -m pytest` do sistema falha:
  Homebrew Python 3.14 sem `psycopg`/`ooh_pipeline` instalados). **11/11 passou** (0 falhas, 0 skips),
  exatamente os 11 testes novos esperados.

## Decisões / desvios

- **`line_poi` materializa TODOS os grupos OOH, não só os 4 do score.** As 2.168.837 linhas cobrem 9
  grupos: `outros` (1.260.581), `servicos` (364.221), `comercio`, `alimentacao`, `saude`,
  `religioso_comunitario`, `automotivo`, `lazer_cultura`, `educacao`. Os **4 grupos pontuados** batem ao
  POI exato com `line_metrics.n_poi_*` (divergência total = 0), e o `Σ n_poi_total` do `core` =
  **todas** as linhas de `line_poi` (= 2.168.837). Ou seja: `line_poi` é a camada bruta de prospecção
  projetada; o intel filtra por `grupo_ooh` no read. Coerente com o épico-3 (densidade scoreável separada
  do bruto multi-fonte).
- **4326 inferido por ausência de `crs`, não por `crs:4326` explícito.** O contrato GeoJSON RFC 7946 trata
  feature sem membro `crs` como WGS84. Tanto `line_corridor` quanto `line_poi` têm **0 registros com campo
  `crs`** e 0 com `type` errado → conformidade por construção. (Validação por `type`+ausência de `crs`, não
  por `ST_IsValid`, que não se aplica ao texto GeoJSON já serializado.)
- **Buffer 300m reusado do T11, não recomputado.** O polígono de `line_corridor` vem do mesmo `ST_Buffer`
  do shape do corredor (T11) serializado com `ST_AsGeoJSON`; POIs de `line_poi` reusam o filtro espacial
  do T12. Sem novo `ST_*` introduzido na camada de leitura — exatamente o objetivo (ADR-003).
- **Idempotência confirmada:** `line_corridor` = 303 rows / 303 distinct `line_id` (1 por linha, zero
  duplicata) após o `INSERT…SELECT` server-side version-stamped.

## Estado do `dataset_version`

- `serving.dataset_version`: **v5 ACTIVE** + **v4 ARCHIVED** (= exatamente 1 ACTIVE). ✅
- `line_corridor` e `line_poi` carimbados em **`version_id=5`** (a ACTIVE corrente) — sem o carimbo órfão
  que afligiu a T8b (lá o `core` ficou preso em v1 inexistente). Aqui a camada de serving nasce já na
  versão viva, então não há divergência de versão a reconciliar.

## Adversarial — o que o cético tentou (refuted: false)

- **Tentou derrubar os 3 critérios de pronto e falhou.** Reconferi independente via MCP, sem reimplementar:
  (1) `line_corridor` 303 rows/303 distinct e `line_poi` 303 distinct line_id; (2) os 4 grupos pontuados
  batem ao POI com `line_metrics.n_poi_*` (122.012 / 193.599 / 28.041 / 74.618) e `Σ n_poi_total` =
  2.168.837 = rows de `line_poi`, **divergência = 0**; (3) GeoJSON 100% válido — 303 `Polygon`,
  2.168.837 `Point`, 0 null / 0 type-errado / 0 com `crs`. Todos batem AGORA na v5. Gate de dados:
  **sobreviveu**.
- **Suspeitou de carimbo de versão órfão (o pecado da T8b) e não confirmou.** `line_corridor`/`line_poi`
  estão em `version_id=5`, que **é** a ACTIVE — não numa BUILDING que sumiu no ciclo de versões. A
  hipótese de "carimbo órfão" não se sustenta nesta task.
- **Atacou o universo de linhas (304 vs 303) e não derrubou.** Igual à T8b/Épico 3: a linha sem itinerário
  GTFS (`320`, sem `line_stop`) fica fora por construção; o critério ("303 linhas") cobre todas as
  elegíveis. A 304ª é inelegível, não falha de materialização.
- **Atacou o isolamento de leitura (grants) e não derrubou.** `ooh_intel_ro` tem SELECT em
  `serving.line_corridor`/`line_poi` e é **negado** em `core.line_metrics` — o intel só enxerga o serving
  flat, como manda o contrato (ADR-052, fronteira dados↔consumidor).
- **Limite reconhecido (não derruba o done):** a suite **completa** do repo não foi rodada (escopo do
  estágio Test — só os 11 testes novos da EP1-02 correram, 11/11). E a conformidade 4326 é inferida por
  ausência de `crs`, não asserida explicitamente — aceitável sob RFC 7946, mas é uma convenção implícita
  que vale carimbar em `COMMENT` se um consumidor futuro reprojetar.

## Tracking

- Run **canônico** gravado aqui no hub de PM (`uai-ooh-pm/docs/epicos/runs/EP1-02-camadas-corredor.md`),
  fechando o gate de localização desde o início — sem o gap pós-hoc que travou a T8b.
- Próximo consumidor: **EP2-08** (intel expõe `line_corridor`/`line_poi` como GeoJSON) → **EP4-04** (mapa).
