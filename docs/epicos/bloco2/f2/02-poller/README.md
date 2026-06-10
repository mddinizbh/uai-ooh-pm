# 02 · poller — 🟨 DATA — ✅ ENTREGUE (2026-06-09)

> Repo: **`uai-ooh-realtime-poller`** *(Python)* · poll do GTFS-RT vehicle-positions, land no `raw` + publish no Kafka.
> **✅ Lane entregue** — repo completo (testes, CI GHCR, Dockerfile non-root, healthcheck), registrado no compose do
> `uai-infra`, **streaming desde 09/jun** (commits até `f69f54a` — "publica a frota inteira em ooh.rt.position").
> Evidências no run [`F2-infra-poller-golive.md`](../../../runs/F2-infra-poller-golive.md).

| Task | O que | Estado |
|---|---|---|
| [POLL-01](POLL-01-scaffold.md) | scaffold (repo separado, Python) + config + clients (confluent-kafka, psycopg) | ✅ `3de5f8c` · CLI `python -m poller` (forever/once/healthcheck) |
| [POLL-02](POLL-02-polling-resiliencia.md) | **polling**: decode (gtfs-realtime-bindings) + resiliência (retries/backoff/UA, **idempotência por `_feed_timestamp`**) + métricas | ✅ `1120d28` · testes de decode/backoff/ciclo |
| [POLL-03](POLL-03-landing-publish.md) | **landing** `raw` (particionado) **+ publish** Kafka (`key=vehicle_id`, lote/ciclo) · land-then-publish · at-least-once | ✅ `36a96e9`/`f69f54a` · IT raw+Kafka |

**Entregue além do planejado:** `poller/active.py` (`KafkaActiveRegistry` consumindo `ooh.vehicle.status`
compactado — gancho do Bloco 3, **não usado no F2** que é frota inteira).

**Medições reais (E0 + go-live):** feed regenera ~15–30s; ~613 veíc/ciclo à noite, **~1.828 no pico da manhã**
(supera os ~451 de referência); `speed` e `license_plate` 100% NULL no feed (velocidade é derivada — CONS).
