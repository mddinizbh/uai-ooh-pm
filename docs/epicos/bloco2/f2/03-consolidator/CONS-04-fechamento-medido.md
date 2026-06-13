# CONS-04 — fechamento: snap + cobertura H3 exata → `medido.*` + evento enriquecido

> F2 (Bloco 2) · lane **consolidator** · **Repo-alvo:** `uai-ooh-trip-consolidator` · **Stack:** Java + PostGIS (leitura no `core`) + h3-java
> **Depende de:** CONS-03. **É a saída MEDIDA que a lane 06 injeta no modelo face-centric.** · *(Replanejado 2026-06-10 — F2-#3/#5/#10; substitui o antigo "CONS-04 metricas-saida" que escrevia `core.trip_executed`)*

## Objetivo
Ao **fechar a viagem** (CONS-03), calcular **km / completude / velocidade por ponto / cobertura H3 exata** via **snap no shape** e persistir no **schema `medido`** + publicar **`ooh.trip.completed` enriquecido**.

## Cálculo (snap no shape, PostGIS em leitura no `core`, SRID 31983)
- **Snap:** projeta as posições no `core.line_shape` (`ST_LineLocatePoint`/`ST_ClosestPoint`) → progressão fracionária.
- **km:** distância percorrida **pelo snap** (não pela soma de GPS ruidoso).
- **completude:** % do shape coberto (range do snap / `current_stop_sequence` vs máx do `pattern_stop`).
- **velocidade por ponto:** snap + timestamps **da entity** (não do header — defasagem p95 ~102s, E0) → `v_real` em frente a cada parada.
- **Cobertura H3 EXATA:** o feed reporta por veículo a cada ~1–2min → entre pings o carro atravessa hexágonos sem registro. **Interpolar o trecho entre pings ao longo do shape** e emitir os hexes intermediários com `fonte='interpolado'` (os com ping ficam `fonte='ping'`). Ponto longe do shape (> limiar) ⇒ trecho **não** interpolado (provável desvio — não mentir cobertura).

## Saída (tudo no `medido` — Flyway do CONS-01; DDL na [techspec](techspec.md))
- **`medido.viagem`** (1 linha/viagem) — **sem `campaign_id`, sem divergência (F2-#5)**; chave única `(vehicle_code, trip_id_rt, service_date, started_at)` + `ON CONFLICT DO NOTHING` (reprocesso seguro).
- **`medido.viagem_hex`** — *(viagem × h3_index × faixa_horaria)* com dwell e fonte. **É o grão do recompute (lane 06).**
- **`medido.viagem_track`** — replay (~1/30s).
- **`medido.parada_velocidade`** — `v_real` por parada (o pipeline agrega pro modelo).
- **`ooh.trip.completed` ENRIQUECIDO (F2-#10):** `viagemId, vehicleCode, lineId, serviceDate, startedAt, endedAt, km, completude, hexesCobertos, faixasHorarias` — o consumidor (intel cache; CMS no B3) **não acessa `medido`/`core`**. Sem track no payload.
- Estado live da viagem zerado no Redis (CONS-05) após persistir.

## Decisões
- **Snap 1×/viagem** (não por posição). Track ~1/30s (≈ todos os pings na prática).
- Ordem: **persiste `medido` → publica evento** (fato durável primeiro; at-least-once no evento).

## Critério de pronto (verificável no banco `ooh` via MCP `postgres-ooh`)
- Viagens da **4107**: km ~ extensão × viagens; `viagem_hex` contíguo ao corredor da linha (ping∪interpolado); `v_real` preenchido; evento publicado. **→ destrava RECAL-01 (lane 06) e RT-02.**

## Produz
- docs/epicos/runs/CONS-04-fechamento-medido.md
