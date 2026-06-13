# F2 — INFRA-01/02 + POLL-01..03 go-live (run consolidado, backfill 2026-06-10)

> Backfill: as lanes 01-infra e 02-poller do F2 foram executadas em 2026-06-05..09 **sem run registrado**
> (convenção do repo). Este run consolida as evidências, levantadas em 2026-06-10 durante o replanejamento
> do F2 (ver `docs/epicos/bloco2/f2/README.md`, decisões F2-#3..#10).

## O que foi entregue

### INFRA-02 — `raw.rt__vehicle_position` particionado (2026-06-05)
- `uai-ooh-pipeline` commit `3a0bdaf`: `ingestor/rt_raw_ddl.py` — `PARTITION BY RANGE (_feed_timestamp)`,
  1 partição/dia, índice btree `(vehicle_id, trip_id, feed_timestamp)`; `ingestor/rt_raw_retention.py` —
  job roda-e-sai dropando partições > `OOH_RT_RAW_RETENTION_DAYS` (default 30).
- Ambos expostos no CLI (`python -m ooh_pipeline ingestor rt-raw-ddl | rt-raw-retention`); teste
  `test_rt_raw_infra02.py` valida DDL/partição.

### INFRA-01 — tópicos Kafka + Redis (2026-06-09)
- `uai-infra` commits `f122424`/`f9b0510`: serviço `kafka-init` no compose cria
  **`ooh.rt.position`** (3 partições, retention 6h, key=`vehicle_id`) e **`ooh.vehicle.status`**
  (compactado, key=`vehicle_code` — bônus além do card, gancho do Bloco 3).
- Poller registrado no compose com `depends_on: postgres-ooh, kafka-init` (commit `d9a6cd9` evita
  auto-create de tópico). Redis já estava de pé (gate 2026-06-06).

### POLL-01..03 — `uai-ooh-realtime-poller` (2026-06-09)
- Repo novo (convenção `gh repo create` + `main` vazia → template). Commits principais:
  `3de5f8c` (scaffold/CLI), `5252fbe` (active registry), `1120d28` (polling/decode/resiliência),
  `36a96e9`/`f69f54a` (landing+publish; **frota inteira** no tópico — decisão 2026-06-10 do guia).
- Ciclo: fetch (retries/backoff/UA) → decode (`gtfs-realtime-bindings`) → **land-then-publish**
  (COPY na partição do dia → produce em lote, `key=vehicle_id`) · idempotência por `_feed_timestamp` ·
  at-least-once. CLI `python -m poller` (forever/`--once`/healthcheck). 9 arquivos de teste
  (decode, backoff, ciclo, raw_writer, publisher, active, heartbeat, config) + `pytest -m integration`.
- Dockerfile multi-stage non-root (uid 10001) + healthcheck; CI GitHub Actions (ruff+pytest →
  build/push GHCR `:latest`+`:sha` em push na main).
- **Extra além do planejado:** `poller/active.py` — `KafkaActiveRegistry` consome `ooh.vehicle.status`
  (compactado). **Não usado no F2** (frota inteira); fica pro Bloco 3.

## Validações de fonte (E0 + inspeção 2026-06-10)
- E0 (run no `uai-ooh-pipeline`, `docs/epicos/runs/E0-validacao-feed-rt.md`, GO 2026-06-09): protobuf 1.0,
  `FULL_DATASET`, feed regenera ~15–30s; **`vehicle.id` do feed = `core.vehicle.vehicle_code`**
  (85,7% geral / 89,2% nos 5 dígitos) — decisão de identidade F2-#8; `speed`/`license_plate` 100% NULL.
- Volume real: ~613 veíc/ciclo à noite, **~1.828 no pico da manhã** (medido 2026-06-10 ~7h39) — o
  "~451/ciclo" dos cards era referência de outro horário; métricas do poller devem assumir a faixa cheia.
- Feeds irmãos da PBH (mesmo servidor/accesskey, CKAN `gtfs-rt`): `trip-updates` (1:1 com positions,
  `departure.delay` presente — pendência pós-F2, F2-#10b) e `alerts` (vazio hoje).

## Desvios vs cards originais
- Cards previam runs individuais (`INFRA-01-*.md` etc.) — substituídos por este consolidado.
- `ooh.vehicle.status` + active registry não estavam nos cards (antecipação do Bloco 3, inofensiva no F2).
- Throughput de referência atualizado (~451 → 613–1.828 conforme hora).

## Estado ao fechar este run
- Poller **streaming em produção** desde 2026-06-09 (landing no `raw` + publish no tópico).
- Próximo da fila: lane **03-consolidator** (specs replanejados — escreve no schema `medido`, F2-#3..#5).
