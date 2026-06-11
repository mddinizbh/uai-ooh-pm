# LOCAL-02 — replayer do feed (raw → Kafka local, acelerado)

> F2 (Bloco 2) · lane **local** · **Repo-alvo:** `uai-ooh-realtime-poller` · **Stack:** Python (reusa clients do poller)
> **Depende de:** LOCAL-01. **Destrava:** parte `e2e` (alimenta o consolidador local com dias de operação em minutos).

## Objetivo
Modo **replay** no poller: ler `raw.rt__vehicle_position` (do Postgres **local**, seedado pelo
LOCAL-01), reagrupar por `_feed_timestamp` (1 grupo = 1 ciclo do feed) e **re-publicar os ciclos no
Kafka local** na ordem original, com cadência acelerada — simulação **determinística**: o mesmo raw
produz sempre o mesmo stream.

## Como executar
- `python -m poller replay [--speed 60] [--from 2026-06-10] [--to 2026-06-11] [--lines 4107,...]`
  - lê os ciclos ordenados por `_feed_timestamp`; publica cada ciclo no tópico (`key=vehicle_id`,
    mesmo formato do publisher normal — reusa `publisher.py`);
  - `--speed 60`: intervalo entre ciclos = real/60 (um dia ≈ 24 min); `--speed 0` = sem pausa;
  - **não landa de volta no raw** (replay é só publish — o raw local já tem o dado);
  - `--lines` opcional pra replay focado (ex. só a 4107) em depuração.
- Config pelas mesmas envs do poller (aponta pro Kafka/Postgres **locais** do LOCAL-01).
- ⚠️ Timeout de viagem do consolidador (10min reais) deve ser configurável por env
  (`TRIP_TIMEOUT_S`) pra acompanhar o speed do replay — anotar no CONS-03 ao implementar.

## Decisões
- **Replay > rodar o poller live local**: horas de operação viram minutos, o resultado é
  reproduzível (mesma entrada → mesma saída), e dá pra repetir o E2E quantas vezes precisar.
- Mora no repo do poller (reusa decode/publisher/config) como subcomando — não é repo novo.

## Critério de pronto (verificável)
- Replay de 1h de raw → nº de lotes no tópico == nº de ciclos distintos do intervalo; ordem por
  veículo preservada (offsets por partição monotônicos no tempo do feed); replay repetido produz
  o mesmo resultado no consolidador (mesmas viagens no `medido` local).

## Produz
- docs/epicos/runs/LOCAL-02-replayer-feed.md
