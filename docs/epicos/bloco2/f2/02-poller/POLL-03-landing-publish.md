# POLL-03 — landing no `raw` + publish no Kafka

> F2 (Bloco 2) · lane **poller** · **Repo-alvo:** `uai-ooh-realtime-poller` · **Stack:** Python
> **Depende de:** POLL-02 + INFRA-01 (tópico) + INFRA-02 (`raw` particionado).

## Objetivo
Por ciclo, **landar a frota inteira** no `raw.rt__vehicle_position` (durável) **e publicar** o lote no Kafka `ooh.rt.position` (stream pro consolidador).

## Como executar
- Após o decode (POLL-02): garantir a **partição do dia** existe (cria se preciso).
- **Landing:** `COPY`/batch insert no `raw.rt__vehicle_position` (TEXT, fiel ao feed) — frota inteira.
- **Publish:** produce no Kafka `ooh.rt.position`, **`key=vehicle_id`**, value = posição (JSON/avro), **lote por ciclo**.
- **At-least-once:** duplicatas toleradas — a idempotência por `_feed_timestamp` (poller) + o estado por veículo (consolidador) absorvem.

## Decisão
- **Ordem: land-then-publish** (o `raw` é a verdade durável; o Kafka é derivado). At-least-once no Kafka (sem exactly-once — o consolidador é idempotente por estado).

## Critério de pronto (verificável)
- Cada ciclo → **N linhas no `raw`** (partição do dia) **+ N mensagens** no tópico (`key=vehicle_id`); contagens batem (~451); lag baixo; sem duplicar landing por `_feed_timestamp`.

## Produz
- docs/epicos/runs/POLL-03-landing-publish.md
