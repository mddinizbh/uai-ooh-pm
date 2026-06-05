# Run — Decisão: IDs GTFS permanecem `text` · 2026-06-05

> Investigação de tipos das colunas de ID no schema `core` do banco `ooh`, via MCP `postgres-ooh`.
> Gatilho: dúvida se `line_id` (hoje `text`) deveria virar `bigint`/`integer`. **Decisão: manter `text`.**
> Candidato a virar ADR numerado (vault-canônico) na promoção via `/vault-update`.

## O que foi verificado (counts reais, todas as versões)

Varredura de **todas as colunas tipo `text` que parecem ID** no schema `core`, sobre todas as linhas:

| Coluna | Tabelas | 100% numérico? | Faixa numérica real | Maior tipo int que caberia | Veredito |
|---|---|:---:|---|---|---|
| `line_id` | line, line_metrics, line_profile_demografico, line_shape, line_stop, trip_pattern, vehicle_line_history | ✅ | 561.787 → **1.014.535** (7 díg.) | `integer` (4 bytes) | mantém `text` |
| `stop_id` | stop, line_stop, pattern_stop | ✅ | 14.784.087 → 34.305.732 | `integer` | mantém `text` |
| `vehicle_id` | vehicle, vehicle_line_history | ✅ | 10.601 → 41.197 | `integer` | mantém `text` |
| `rt_vehicle_id` | vehicle (2.288/2.653 nulos) | ✅ | 10.753 → 41.196 | `integer` | mantém `text` |
| `shape_id` | line_shape, trip_pattern | ✅ | 1 → 1.083 | `smallint` | mantém `text` |
| `fonte_id` | poi (248.353 linhas) | ❌ (248.352 não-numéricos, até 36 chars) | UUID/identificador externo | — | `text` **obrigatório** |

Notas técnicas:
- Sem zero à esquerda em nenhum ID numérico (não há perda semântica por leading zero).
- `line_id` cabe folgado num `integer` comum (máx ~1M « 2,1 bi) — `bigint` seria sobra. Nenhum ID precisa de `bigint`.
- `vehicle_line_history.line_id` tem 101 nulos (coluna nullable lá); demais `line_id` sem nulos.

## Decisão

**Manter todas as colunas de ID como `text`.** Não migrar para `bigint`/`integer`.

**Por quê:**
- A **spec GTFS** define `route_id`/`stop_id`/`shape_id`/`trip_id` como **string**. O feed BHTrans hoje é
  numérico, mas a spec permite IDs alfanuméricos. Migrar para inteiro quebraria ingestão se o feed mudar
  de formato (ex.: stop `34305732N`, código com letra).
- Ganho de perf/storage de `text→bigint` (índices/joins) **não compensa** o risco de fragilizar o pipeline
  de ingestão, que é o caminho mais sensível a fonte externa.
- `poi.fonte_id` é `text` obrigatório de qualquer forma (identificadores externos, não-numéricos, 36 chars).

**Como aplicar:** ao desenhar `serving` (T17) e o domínio do `uai-ooh-intel` (T19), tratar IDs como `String`,
não `Long`. Nada de cast para inteiro no caminho de leitura.
