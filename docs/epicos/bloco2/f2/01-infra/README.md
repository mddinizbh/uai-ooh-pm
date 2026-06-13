# 01 · infra — 🟫 INFRA — ✅ entregue (01/02) + 🔲 INFRA-03 (tiering)

> Repo: **`uai-infra`** (Docker Compose / VPS) + **`uai-ooh-pipeline`** (DDL/jobs do `raw`) · provisiona o que o realtime precisa.
> INFRA-01/02 ✅ — evidências no run [`F2-infra-poller-golive.md`](../../../runs/F2-infra-poller-golive.md).
> **INFRA-03 criada em 2026-06-10:** VPS pequena → `raw` quente curto + histórico frio no MinIO.

| Task | O que | Estado |
|---|---|---|
| [INFRA-01](INFRA-01-kafka-topic-redis.md) | tópico **`ooh.rt.position`** (`partitions=3`, `key=vehicle_id`, `retention=6h`) + Redis confirmado | ✅ `uai-infra` `f122424`/`f9b0510` · **+ bônus:** tópico **`ooh.vehicle.status`** (compactado, key=`vehicle_code`) criado junto |
| [INFRA-02](INFRA-02-raw-particionado.md) | `raw.rt__vehicle_position` **particionado por dia** + retenção 30d | ✅ `uai-ooh-pipeline` `3a0bdaf` · `ingestor rt-raw-ddl` + `ingestor rt-raw-retention` no CLI |
| [INFRA-03](INFRA-03-arquivamento-raw-minio.md) | **tiering**: quente 7d no Postgres → parquet/zstd no **MinIO** → drop (evolui o `rt-raw-retention`) | 🔲 · alívio imediato: `OOH_RT_RAW_RETENTION_DAYS` 30→14 no compose |
| [INFRA-04](INFRA-04-automacao-f2.md) | **automação F2**: consolidador no compose (serviço contínuo) + crons (archive diário, recompute D-1, HLLs pós-rebuild) | 🔲 · fecha junto com a lane CONS |

**Destravou:** POLL (✅ publicando desde 09/jun) e CONS (consome o tópico + estado no Redis).
**Princípio uAI:** Kafka/Redis em 127.0.0.1; deploy só por GitHub Actions; nunca tocar o VPS direto.
