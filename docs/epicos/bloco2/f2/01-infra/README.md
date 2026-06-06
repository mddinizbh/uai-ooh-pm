# 01 · infra — 🟫 INFRA

> Repo: **`uai-infra`** (Docker Compose / VPS) · provisiona o que o realtime precisa.
> **✅ Gate resolvido (2026-06-06): Kafka (KRaft) + Redis já estão de pé.**

| Task | O que | Nota |
|---|---|---|
| INFRA-01 | **Kafka/Redis ✅ já de pé** → criar só o **tópico `ooh.rt.position`** (partições/retenção) + confirmar acesso 127.0.0.1 | gate resolvido |
| INFRA-02 | `raw.rt__vehicle_position` **particionado por dia** + retenção N dias (auditoria/calibração) | btree `(vehicle_id,trip_id,timestamp)` + índice por dia |

**Destrava:** POLL (publica no tópico + landa no raw) e CONS (consome o tópico + estado no Redis).
**Princípio uAI:** Kafka/Redis em 127.0.0.1; deploy só por GitHub Actions; nunca tocar o VPS direto.
