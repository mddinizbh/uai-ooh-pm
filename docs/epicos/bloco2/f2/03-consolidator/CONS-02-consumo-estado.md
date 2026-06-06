# CONS-02 — consumo + estado por veículo (Redis)

> F2 (Bloco 2) · lane **consolidator** · **Repo-alvo:** `uai-ooh-trip-consolidator` · **Stack:** Java + Spring Kafka + Redis
> **Depende de:** CONS-01 + INFRA-01 (tópico).

## Objetivo
Consumir `ooh.rt.position` e manter o **estado da viagem em andamento por veículo** em Redis. **Modo frota inteira** (F2 sem filtro de ativos).

## Como executar
- Consumer com `key=vehicle_id` → cada veículo cai sempre na **mesma partição** → **ordem temporal por veículo** garantida.
- Para cada posição: atualiza o **estado por veículo** em Redis (hash `veh:{vehicle_id}`): `current_trip_id`, `current_stop_sequence`, `last_feed_ts`, `started_at`, `last_lat/lon`, ponteiros do traçado acumulado.
- **Idempotência:** ignora posição com `feed_timestamp` ≤ `last_feed_ts` (absorve o **at-least-once** do poller).
- **Frota inteira:** processa **todos** os veículos (no Bloco 3, filtra pelo conjunto de ativos lido do Redis — gancho previsto, não implementado).

## Decisão
- **Estado em Redis** (hash por `vehicle_id`, TTL de segurança p/ veículos que somem). Processar **todos** no F2.

## Critério de pronto
- Estado por veículo mantido em Redis e atualizado em ordem; **duplicatas absorvidas** (idempotência por `feed_timestamp`); throughput acompanha ~451/ciclo sem lag crescente.

## Produz
- docs/epicos/runs/CONS-02-consumo-estado.md
