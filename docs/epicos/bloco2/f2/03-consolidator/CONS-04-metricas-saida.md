# CONS-04 — km / completude / velocidade + saída (`trip_executed`)

> F2 (Bloco 2) · lane **consolidator** · **Repo-alvo:** `uai-ooh-trip-consolidator` · **Stack:** Java + PostGIS (`core`)
> **Depende de:** CONS-03. **É a saída verificada que calibra o F1.**

## Objetivo
Ao **fechar a viagem** (CONS-03), calcular **km / completude / velocidade por ponto** via **snap no shape** e **persistir** + emitir evento.

## Cálculo (snap no shape do `trip_pattern`, PostGIS no `core`)
- **Snap:** projeta as posições no shape (`ST_LineLocatePoint`/`ST_ClosestPoint`) → progressão fracionária ao longo do trajeto.
- **km:** distância percorrida **pelo snap** (não pela soma de GPS ruidoso).
- **completude:** % do shape coberto (do range do snap / `current_stop_sequence` vs `pattern_stop` máx).
- **velocidade por ponto:** do snap + timestamps → velocidade do carro **em frente a cada ponto** do trajeto → `pattern_stop_exposure.v_real` (alimenta o `f_vel` **real**).

## Saída
- **`core.trip_executed`** (1 linha/viagem): `vehicle_id`, `line`, `pattern`, `service_date`, início/fim, duração, **km**, pontos_servidos, **completude**, `campaign_id` **NULL** (F2), `divergencia_linha` NULL (F2).
- **`core.trip_executed_track`**: traçado **downsampled ~1/30s** (replay).
- **`pattern_stop_exposure.v_real`** por viagem/ponto.
- Emite **`ooh.trip.completed`**.

## Decisão
- **Downsample do track = ~1/30s** (replay leve). Snap via **PostGIS no `core`** (permitido — não é o intel/serving).

## Critério de pronto (verificável no banco `ooh`)
- Viagens da **4107** fecham com **km ~ extensão × viagens** e **velocidade por ponto** preenchida; `trip_executed` + `trip_executed_track` + `pattern_stop_exposure.v_real` gravados; `ooh.trip.completed` emitido. **→ alimenta o loop verificado→estimado (RT-02).**

## Produz
- docs/epicos/runs/CONS-04-metricas-saida.md
