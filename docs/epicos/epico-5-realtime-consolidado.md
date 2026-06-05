# Épico 5 — Tempo real + consolidado de viagem (F2)

> Bloco 2 do roadmap (`docs/arquitetura-servicos.md`). Poller do GTFS-RT + Kafka + consolidador de
> viagens + realtime no `intel`. **Depende de:** Épicos 1–3 (`core` com `trip_pattern`/`line_shape`).
> Realtime opera **só nos carros ativos** (registro de ativação — pré-requisito mínimo aqui).

## Tarefa 5.0 — Registro mínimo de carros ativos (pré-requisito)
- Versão enxuta do comercial OOH: marcar `vehicle_code` como ACTIVE/ENDED + publicar
  `ooh.vehicle.activated/deactivated`. Pode ser uma tabela `active_vehicle(vehicle_code, line_esperada,
  campaign_id, from, to)` + endpoint interno, antes do `uai-ooh-commercial` completo (Épico 6).
- **Sem isso**, o consolidador roda em modo "frota inteira" só p/ validação técnica.
- **Pronto:** dá pra ativar/desativar um carro e o consolidador reage.

## Tarefa 5.1 — `uai-ooh-realtime-poller` (Python)
- Polling do GTFS-RT vehicle-positions a cada ~15–20s (reusa `load_gtfs_rt.py` p/ decode protobuf).
- **Landa o feed inteiro** em `raw.rt__vehicle_position` (**particionado por dia**, retenção N dias) —
  auditoria/calibração/frequência F1 — **e publica** em Kafka `ooh.rt.position` (lote por ciclo).
- Resiliência: retries, backoff, UA; idempotência por `_feed_timestamp`. Métrica de taxa/lag.
- **Pronto:** stream contínuo no tópico + landing particionado; medir taxa (~451 veíc/ciclo).

## Tarefa 5.2 — `uai-ooh-trip-consolidator` (Java/Spring)
- Consome `ooh.rt.position`; mantém o **conjunto de carros ativos** (de `ooh.vehicle.activated/…`) em
  Redis; **filtra só os ativos**.
- **Reconstrução da viagem:** instância = sequência contígua de `(vehicle_id, trip_id)` no mesmo dia;
  fecha por mudança de trip_id / reset de `current_stop_sequence` / timeout / último ponto do padrão.
  Estado por veículo em **Redis**.
- **Linha do RT, nunca MCO:** `trip_id`→`route_id`→`core.line`/`core.trip_pattern` (96,7% casam; 3,3%
  suplementares → `padrao_desconhecido`, casar por route+direction+geo). **Sinaliza divergência** se a
  linha rodada ≠ a esperada da campanha (alerta de execução).
- **km + completude por snap:** projeta as posições no shape do `trip_pattern` (PostGIS no `core`) →
  km, % de completude, pontos servidos (via `current_stop_sequence`/`pattern_stop`).
- **Velocidade de passagem por ponto:** do snap, calcula a velocidade do carro em frente a cada ponto
  do trajeto → alimenta o **impacto visual verificado** (`f_vel` real; ver Épico 3 / alcance).
- **Saída:** `core.trip_executed` (1 linha/viagem: vehicle, line, pattern, service_date, início/fim,
  duração, km, pontos_servidos, completude, campaign_id, divergencia_linha) + `core.trip_executed_track`
  (traçado **downsampled** ~1/30s, p/ replay) + `pattern_stop_exposure.v_real` por viagem/ponto.
  Emite `ooh.trip.completed`. **O `campaign_id` carimbado é o token de correlação com o comercial
  (ADR-052)** — o `ooh` permanece sem tenant; o `campaign_id` é opaco aqui.
- **Pronto:** viagens da 4107 fecham com nº/dia ~ frequência e km ~ extensão×viagens; divergências sinalizadas.

## Tarefa 5.3 — Realtime read no `uai-ooh-intel`
- Endpoints: posição ao vivo + progresso de viagem **para um conjunto de vehicle_id** (primitivo
  tenant-agnóstico; o conjunto vem do cms/comercial). Lê do **Redis** (última posição/estado).
- Alcance **verificado** por campanha (do `trip_executed` + `pattern_stop_exposure.v_real`).
- SSE/WebSocket p/ o mapa ao vivo; sem PostGIS no serving.
- **Pronto:** mapa ao vivo dos carros ativos de uma campanha + alcance verificado vs estimado.

## Critério de pronto do Épico 5
- Poller publicando `ooh.rt.position` + landing particionado; consolidador fechando `trip_executed`
  só dos ativos, com linha-do-RT + divergência + velocidade por ponto; `intel` servindo realtime do
  conjunto ativo. Validação na 4107 (viagens/dia, km, alcance verificado comparável ao estimado).
