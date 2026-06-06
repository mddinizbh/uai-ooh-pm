# 02 · poller — 🟨 DATA

> Repo: **`uai-ooh-realtime-poller`** *(novo, Python)* · poll do GTFS-RT vehicle-positions, land no `raw` + publish no Kafka.
> **Depende de:** INFRA-01 (Kafka + tópico). **Roda contínuo** (worker).

| Task | O que | Nota |
|---|---|---|
| POLL-01 | scaffold do repo Python + config (URL GTFS-RT, intervalo ~15–20s) | regenerar de template se houver |
| POLL-02 | **polling**: decode protobuf (reusa `load_gtfs_rt.py`) + resiliência (retries/backoff/UA, **idempotência por `_feed_timestamp`**) + métricas (taxa ~451 veíc/ciclo, lag) | |
| POLL-03 | **landing** `raw.rt__vehicle_position` (particionado) **+ publish** Kafka `ooh.rt.position` (lote por ciclo) | landa a **frota inteira** (auditoria/freq F1) |

**Pronto:** stream contínuo no tópico + landing particionado; taxa medida.
