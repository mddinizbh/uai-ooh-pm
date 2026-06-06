# POLL-01 — scaffold do `uai-ooh-realtime-poller`

> F2 (Bloco 2) · lane **poller** · **Repo-alvo:** `uai-ooh-realtime-poller` *(NOVO)* · **Stack:** Python · **worker contínuo**
> **Destrava:** POLL-02/03.

## Objetivo
Criar o repo do poller (worker que roda em loop) + config + clients (Kafka, Postgres). **Regerar de template** se houver base equivalente.

## Decisão
- **Repo separado** (não módulo do `uai-ooh-pipeline`): o poller é **contínuo** (~15–20s), cadência distinta do ingestor batch. *(O `arquitetura-servicos.md` já prevê `uai-ooh-realtime-poller` separado.)*
- **Lib Kafka: `confluent-kafka`** (robusta, librdkafka) — produce com `key=vehicle_id`.

## Como executar
- **Passo 0 — criar o repo:** `gh repo create mddinizbh/uai-ooh-realtime-poller --private` + push de uma **`main` vazia** (commit baseline) **antes de qualquer código**. Só então o scaffold (regerar do template, se houver base).
- Estrutura Python (módulo `poller/`, CLI `python -m poller`), config via **env**: `GTFS_RT_URL`, `POLL_INTERVAL` (15–20s), `KAFKA_BOOTSTRAP`, `KAFKA_TOPIC=ooh.rt.position`, `OOH_DB_URL` (escrita no `raw`).
- Clients: `confluent-kafka` (produce), `psycopg` (COPY no `raw`). Reusa `db.py`/toolchain do pipeline se fizer sentido compartilhar via pacote.
- Dockerfile + healthcheck (último ciclo OK / lag).

## Critério de pronto
- Poller sobe, lê a config, conecta Kafka + Postgres; healthcheck verde; loop vazio roda sem crashar.

## Produz
- docs/epicos/runs/POLL-01-scaffold.md
