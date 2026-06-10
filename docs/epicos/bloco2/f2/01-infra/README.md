# 01 · infra — 🟫 INFRA — ✅ ENTREGUE (2026-06-05..09)

> Repo: **`uai-infra`** (Docker Compose / VPS) + **`uai-ooh-pipeline`** (DDL do `raw`) · provisiona o que o realtime precisa.
> **✅ Lane entregue** — evidências consolidadas no run [`F2-infra-poller-golive.md`](../../../runs/F2-infra-poller-golive.md).

| Task | O que | Estado |
|---|---|---|
| [INFRA-01](INFRA-01-kafka-topic-redis.md) | tópico **`ooh.rt.position`** (`partitions=3`, `key=vehicle_id`, `retention=6h`) + Redis confirmado | ✅ `uai-infra` `f122424`/`f9b0510` · **+ bônus:** tópico **`ooh.vehicle.status`** (compactado, key=`vehicle_code`) criado junto |
| [INFRA-02](INFRA-02-raw-particionado.md) | `raw.rt__vehicle_position` **particionado por dia** + retenção 30d | ✅ `uai-ooh-pipeline` `3a0bdaf` · `ingestor rt-raw-ddl` + `ingestor rt-raw-retention` no CLI |

**Destravou:** POLL (✅ publicando desde 09/jun) e CONS (consome o tópico + estado no Redis).
**Princípio uAI:** Kafka/Redis em 127.0.0.1; deploy só por GitHub Actions; nunca tocar o VPS direto.
