# CONS-02 — consumo + estado por veículo (Redis) + h3 local

> F2 (Bloco 2) · lane **consolidator** · **Repo-alvo:** `uai-ooh-trip-consolidator` · **Stack:** Java + Spring Kafka + Redis + h3-java
> **Depende de:** CONS-01 + INFRA-01 ✅. · *(Replanejado 2026-06-10)*

## Objetivo
Consumir `ooh.rt.position` e manter o **estado da viagem em andamento por veículo** em Redis — incluindo os **hexágonos visitados** (h3 local) que alimentam o acumulado (CONS-05). **Modo frota inteira** (F2-#1, sem filtro de ativos).

## Como executar
- Consumer com key=`vehicle_code` → cada veículo sempre na **mesma partição** → ordem temporal garantida.
- Por posição: atualiza `VehicleState` no Redis (contrato de chaves no CONS-05): `current_trip_id`, `route_id`, `current_stop_sequence`, `last_feed_ts`, `started_at`, última posição, **set de hexes com ping**, contadores.
- **H3 local (F2-#4):** `latLngToCell(lat, lon, 9)` via **h3-java** — O(1), sem banco. Os 2.615 h3_index de `core.h3_cell` carregados em memória no boot; posição fora da grade não acumula hex (mas mantém posição/track).
- **Idempotência:** ignora posição com `feed_timestamp ≤ last_feed_ts` (absorve o at-least-once do poller).
- **Identidade (F2-#8):** `vehicle_code` = `vehicle.id` do feed, direto — sem join com `core.vehicle`; veículo desconhecido processa normal.
- TTL de segurança nos estados (veículo que some é varrido pelo timeout do CONS-03).

## Decisões
- **Estado em Redis** com chaves públicas (CONS-05) — o intel lê as mesmas chaves; o consolidador é a única escrita.
- Throughput de referência: **613–1.828 veíc/ciclo** conforme hora (medições 09–10/jun; o "~451" antigo era de outro horário).

## Critério de pronto
- Estado mantido em ordem; duplicatas absorvidas; hex do ping entra no set; throughput acompanha o pico (~1.828/ciclo) sem lag crescente. IT com embedded Kafka + Redis (Testcontainers).

## Produz
- docs/epicos/runs/CONS-02-consumo-estado.md
