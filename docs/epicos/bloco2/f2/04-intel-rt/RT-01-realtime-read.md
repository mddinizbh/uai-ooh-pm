# RT-01 — realtime read (posição + progresso)

> F2 (Bloco 2) · lane **intel-rt** · **Repo-alvo:** `uai-ooh-intel` *(estende F1)* · **Stack:** Java/Spring
> **Depende de:** intel F1 (EP2) + Redis (estado do CONS).

## Objetivo
Servir **posição ao vivo + progresso de viagem** para um **conjunto de `vehicle_id`** — lendo do **Redis** (estado mantido pelo consolidador). No F2 o conjunto vem de um **filtro por LINHA** (avaliação).

## Como executar
- `GET /api/realtime/positions?line={lineId}` → o **intel resolve no servidor** o conjunto de `vehicle_id` que estão rodando **essa linha agora** (do Redis: veículos cujo `current_trip_id → route_id == lineId`), e devolve por carro: última posição (lat/lon/bearing), `current_stop_sequence`, completude parcial, timestamp.
- Lê **só do Redis** (não toca `core` no hot path). Sem PostGIS.
- **Anti-vazamento:** o conjunto é **sempre server-derived** do escopo (`line`); o cliente **nunca** passa `vehicle_id` arbitrário.

## Decisão
- **Conjunto = filtro por LINHA** no F2 (primitivo tenant-agnóstico; no Bloco 3 o escopo vira campanha+tenant, mesmo endpoint).

## Critério de pronto
- `GET positions?line=4107` → posições + progresso **dos carros da 4107 ao vivo** (do Redis); conjunto derivado no servidor; sem PostGIS.

## Produz
- docs/epicos/runs/RT-01-realtime-read.md
