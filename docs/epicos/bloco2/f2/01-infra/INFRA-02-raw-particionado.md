# INFRA-02 — `raw.rt__vehicle_position` particionado por dia

> F2 (Bloco 2) · lane **infra** · **Repo-alvo:** `uai-ooh-pipeline` (dono do schema `raw`) · **Stack:** Python + PostgreSQL
> **Destrava:** POLL-03 (landa aqui). É o **durável** (auditoria/calibração/frequência), espelho do que vai pro Kafka.

## Objetivo
Criar a tabela de **landing do RT** particionada por dia, com retenção — onde o poller persiste a **frota inteira** (além de publicar no Kafka).

## Como executar
- **Declarative partitioning** `PARTITION BY RANGE` no timestamp do feed (1 partição/dia); criar a partição do dia no startup do poller (ou job).
- **Colunas (landing = TEXT, fiel ao feed):** `vehicle_id`, `trip_id`, `route_id`, `lat`, `lon`, `bearing`, `current_stop_sequence`, `current_status`, `feed_timestamp`, `_feed_timestamp` (idempotência), `ingested_at`.
- **Índices:** btree `(vehicle_id, trip_id, feed_timestamp)` + por partição.
- **Retenção:** dropar partições mais velhas que N dias (job) — o `raw` é **reproduzível do feed**, então retenção curta-média.
- **Sem geometry** no `raw` (lat/lon TEXT; a geometria nasce no `core`/consolidador).

## Decisão
- **Retenção = N dias** (recomendo **30** — janela boa pra calibração/auditoria; ajustável). Partição por **dia** (alinha com `service_date` do consolidador).

## Critério de pronto (verificável no banco `ooh`)
- Tabela particionada criada; partição do dia existe; o **poller landa** (counts > 0 por ciclo); índices presentes; job de retenção configurado.

## Produz
- docs/epicos/runs/INFRA-02-raw-particionado.md
