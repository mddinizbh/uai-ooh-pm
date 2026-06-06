# 01 · infra — 🟫 INFRA

> Repo: **`uai-infra`** (Docker Compose / VPS) · provisiona o que o realtime precisa.
> **✅ Gate resolvido (2026-06-06): Kafka (KRaft) + Redis já estão de pé.**

| Task | O que | Nota |
|---|---|---|
| [INFRA-01](INFRA-01-kafka-topic-redis.md) | **Kafka/Redis ✅ já de pé** → criar só o **tópico `ooh.rt.position`** (`partitions=3`, `key=vehicle_id`, `retention=6h`) + confirmar Redis | gate resolvido |
| [INFRA-02](INFRA-02-raw-particionado.md) | `raw.rt__vehicle_position` **particionado por dia** + retenção ~30d (auditoria/calibração) | btree `(vehicle_id,trip_id,feed_timestamp)` |

**Destrava:** POLL (publica no tópico + landa no raw) e CONS (consome o tópico + estado no Redis).
**Princípio uAI:** Kafka/Redis em 127.0.0.1; deploy só por GitHub Actions; nunca tocar o VPS direto.
