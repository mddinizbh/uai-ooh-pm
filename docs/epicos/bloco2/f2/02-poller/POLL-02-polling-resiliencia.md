# POLL-02 — polling GTFS-RT + decode + resiliência — ✅ ENTREGUE (2026-06-09)

> F2 (Bloco 2) · lane **poller** · **Repo-alvo:** `uai-ooh-realtime-poller` · **Stack:** Python
> **✅ Entregue:** commit `1120d28` — `feed.py` (retries/backoff/UA), `decode.py` (protobuf→FeedSnapshot),
> `poller.py` (idempotência por `feed_timestamp`); testes `test_feed_backoff/test_decode/test_poller_cycle`.
> Run: [`F2-infra-poller-golive.md`](../../../runs/F2-infra-poller-golive.md).

## Objetivo
O **loop de polling** do GTFS-RT vehicle-positions (~15–20s): fetch → decode protobuf → posições normalizadas em memória, com resiliência e métricas.

## Como executar
- Loop a cada `POLL_INTERVAL`; fetch do feed (`FeedMessage` protobuf).
- **Decode:** `gtfs-realtime-bindings` (reusa a lógica do `load_gtfs_rt.py`). Extrair por veículo: `vehicle_id`, `trip_id`, `route_id`, `lat`, `lon`, `bearing`, `current_stop_sequence`, `current_status`, `feed_timestamp`.
- **Idempotência:** `_feed_timestamp` (header do feed) — **não reprocessa o mesmo feed** (se o timestamp não avançou, pula o ciclo).
- **Resiliência:** retries + backoff exponencial, timeout, `User-Agent`, tolerância a feed vazio/HTTP erro (loga e segue, não derruba o worker).
- **Métricas:** taxa (~**451 veíc/ciclo**), **lag** (`now − feed_timestamp`), erros/ciclo.

## Decisão
- **Decode com `gtfs-realtime-bindings`** (oficial). Idempotência por `_feed_timestamp` (já é o padrão do F1).

## Critério de pronto
- Decodifica o feed e extrai ~**451 posições/ciclo**; **idempotente** por `_feed_timestamp`; métricas (taxa/lag) expostas; sobrevive a falha de fetch (retry/backoff) sem crashar.

## Produz
- docs/epicos/runs/POLL-02-polling-resiliencia.md
