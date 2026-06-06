# 02 · poller — 🟨 DATA

> Repo: **`uai-ooh-realtime-poller`** *(novo, Python)* · poll do GTFS-RT vehicle-positions, land no `raw` + publish no Kafka.
> **Depende de:** INFRA-01 (Kafka + tópico). **Roda contínuo** (worker).

| Task | O que | Nota |
|---|---|---|
| [POLL-01](POLL-01-scaffold.md) | scaffold (repo separado, Python) + config + clients (confluent-kafka, psycopg) | regerar de template |
| [POLL-02](POLL-02-polling-resiliencia.md) | **polling**: decode (gtfs-realtime-bindings) + resiliência (retries/backoff/UA, **idempotência por `_feed_timestamp`**) + métricas (~451/ciclo, lag) | |
| [POLL-03](POLL-03-landing-publish.md) | **landing** `raw` (particionado) **+ publish** Kafka (`key=vehicle_id`, lote/ciclo) | land-then-publish · at-least-once |

**Pronto:** stream contínuo no tópico + landing particionado; taxa medida.
