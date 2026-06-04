entao# Épico 1 — Identidades do `core` (linha · ponto · itinerário · veículo)

> Normalização F1, parte 1. Constrói as entidades-base do `core` no banco `ooh` (PostGIS), a
> partir do `raw`. Ver `docs/plano-normalizacao-core.md` para o desenho completo e as decisões.
> **Depende de:** Épico 0 fechado (renda carregada; trânsito = exposição arterial). Nada mais.
>
> **Atualização (pós-plano de serviços):** a unidade canônica do itinerário é o **padrão de viagem**
> `core.trip_pattern` (shape + sequência de pontos, por linha/direção) + `core.pattern_stop` (pontos
> do padrão) + `pattern_service` (em que dias/tipo roda). `service_type` é **atributo do padrão**,
> não a chave. As tarefas 1.3/1.4 (line_shape/line_stop por service_type) são materializações de
> apresentação; o modelo de verdade é o **pattern** (resolve o "ponto solto": cada ponto pertence ao
> seu padrão e está sobre a linha). Índices GiST/btree conforme `arquitetura-servicos.md`.

**Onde roda:** banco `ooh`, schema novo `core`. Pipeline em Python (`.data/scripts/`, reusa
`db.py`) + SQL PostGIS. Idempotente: cada script faz `CREATE SCHEMA IF NOT EXISTS core` e
recria/preenche sua(s) tabela(s). Versionar por `dataset_version` (criar a tabela de controle
espelhando o app; usar 1 versão "sim-F1" por enquanto).

**Regra de ouro (confirmada na simulação):** geom PBH no `raw` tem **SRID=0** →
`ST_SetSRID(...,31983)`; GTFS/OSM `ST_Transform(... ,4326→31983)`; censo `4674→31983`. Coords/
números com vírgula e lixo (`#####`, `X`) → guardar regex `~ '^-?[0-9.]+$'` antes de castar.

**Índices na `raw` (criar ao rodar):** geo via **temp-table GiST** (padrão `build_core_stop`); chaves de junção via `CREATE INDEX IF NOT EXISTS` + `ANALYZE` na raw e **deixar** (padrão `build_core_trip_pattern`). Por tarefa: **1.2 stop** = temp-GiST da geom de `pbh__ponto_onibus`; **1.3/1.4 pattern** = `gtfs__stop_times(trip_id)`, `gtfs__shapes(shape_id)`, `gtfs__trips(route_id)`; **1.5 vehicle** = `pbh__mco_consolidado(veiculo)`. Detalhe em `arquitetura-servicos.md` §"Índices na `raw`". Persistência: `INSERT…SELECT` server-side; volume no Python → reusar a **classe de persistência em chunks** do pipeline.

---

## Tarefa 1.0 — Esquema e controle de versão
- `CREATE SCHEMA core;`
- `core.dataset_version(version BIGSERIAL PK, imported_at timestamptz default now(), fonte text, status text check(status in ('ACTIVE','BUILDING','ARCHIVED')))`. Inserir 1 versão `BUILDING`, virar `ACTIVE` no fim do Épico 3.
- **Pronto:** schema criado; 1 `dataset_version` ativa para o build F1.

## Tarefa 1.1 — `core.line`
- DDL: `id BIGSERIAL PK, short_name, long_name, gtfs_route_id, route_type, categoria, is_circular_sc bool, is_suplementar bool, extensao_m numeric, dataset_version FK`.
- Fonte: `raw.gtfs__routes`. `short_name = route_short_name` (chave natural). `categoria`/flags por
  regex no prefixo: `is_circular_sc = short_name ~ '^SC'`; `is_suplementar = short_name ~ '^S' AND NOT ~ '^SC'`.
- `extensao_m` = preenchido na 1.3 (shape representativo). 304 linhas esperadas.
- **Validação:** `count(*)=304`; nenhuma `short_name` nula/duplicada na versão.
- **Pronto:** 304 linhas com flags de categoria.

## Tarefa 1.2 — `core.stop` (reconciliação GTFS↔PBH por geo)
- DDL: `id, gtfs_stop_id, siu, match_dist_m numeric, name, geom_31983 geometry(Point,31983), lat double, lon double, dataset_version`. Índice **GiST** em `geom_31983`.
- Fonte: `raw.gtfs__stops` (verdade dos pontos; lat/lon 4326 → geom 31983). `siu` por **KNN ≤50m**
  contra `raw.pbh__ponto_onibus` (`ST_DWithin(...,50)` + `<->` ORDER BY LIMIT 1); guardar
  `match_dist_m`. `siu` NULL se sem vizinho ≤50m.
- **Validação (alvo confirmado na sessão):** ~99% dos stops com `siu` não-nulo; `match_dist_m`
  mediana ~0m, p90 <5m, máx ~60m. Reportar histograma e os sem-match.
- **Pronto:** ~9.650 stops; coluna `siu` populada; índice GiST criado.

## Tarefa 1.3 — `core.line_shape` (shape representativo por sentido **e tipo de dia**)
- DDL: `id, line_id FK, service_type text check in ('util','sabado','domingo','especial'), direction smallint check(0,1), gtfs_shape_id, geom_31983 geometry(LineString,31983), geom_geojson jsonb, comprimento_m numeric, dataset_version`. Índice GiST.
- Fonte: `raw.gtfs__shapes`+`trips`+`routes`+`calendar`. **Lição crítica da simulação (4107):** o
  itinerário **varia por dia da semana** — a 4107 tem shapes distintos para `Dias Úteis`, `Sábados`
  e `Domingos` (provável desvio da Feira Hippie no domingo). Logo o shape NÃO é só por sentido:
  derivar `service_type` do `trips.service_id` via `gtfs__calendar` (sunday/saturday/monday) e pegar
  **o shape mais longo por (service_type, direction)**. Serviços de calendar_dates sem linha em
  `calendar` (ex.: carnaval) → `especial` (ou ignorar em F1).
- `geom_geojson` = `ST_AsGeoJSON(ST_Transform(geom_31983,4326))` para web. `core.line.extensao_m`
  = comprimento do representativo **útil** (referência).
- **Validação (bate com a simulação):** 4107 tem 3 service_types × 2 sentidos; domingo dir1 ≈ 14,5 km
  vs útil dir1 ≈ 21,4 km (itinerários geometricamente distintos). Os `core.line_stop` de cada serviço
  caem sobre o shape do serviço correspondente (resolve o "ponto solto" visto na simulação).
- **Pronto:** shape representativo por (serviço, sentido); a ficha/UI pode alternar itinerário por dia.

## Tarefa 1.4 — `core.line_stop` (sequência ponto×linha×sentido×tipo de dia)
- DDL: `id, line_id FK, service_type text, stop_id FK, direction smallint, stop_sequence int, is_terminal bool, dataset_version`. UNIQUE(line_id,service_type,direction,stop_sequence,version).
- Fonte: `raw.gtfs__trips`+`stop_times`+`calendar`. Para cada (linha, service_type, sentido), pegar o
  **trip representativo** (o do shape de 1.3) e materializar a sequência ordenada por `stop_sequence`;
  `is_terminal` no 1º/último. Como o itinerário muda por dia (ver 1.3), a sequência de pontos também —
  por isso a chave inclui `service_type`. Resolve o `LineDetail.stops` vazio do app.
- **Validação:** 4107 ≈ 189 stops distintos no total; os pontos do serviço domingo caem sobre o
  shape de domingo.
- **Pronto:** sequência de pontos por linha/sentido/tipo de dia.

## Tarefa 1.5 — `core.vehicle` + `core.vehicle_line_history` (auxiliar)
- DDL: `core.vehicle(id, veiculo_code unique, rt_vehicle_id, empresa)` e
  `core.vehicle_line_history(id, vehicle_id FK, line_short_name, tipo_dia smallint, n_viagens int, observed_from date, observed_to date)`.
- Fonte: `raw.pbh__mco_consolidado` (`veiculo`,`linha`,`tipo_dia`,`viagem`) + `raw.rt__vehicle_position` (`vehicle_id`). `rt_vehicle_id` = match `veiculo=vehicle_id` (~81%).
- **F1 = auxiliar** (suporta oferta/validação de frota; não alimenta score/impressões). Pode ser
  feito por último ou adiado se apertar.
- **Pronto:** frota distinta da 4107 = 31 carros (bate com a sessão); ~81% com `rt_vehicle_id`.

---

## Critério de pronto do Épico 1
- `core` com `line` (304), `stop` (~9,6k, com `siu`/geo), `line_shape` (representativo/sentido),
  `line_stop` (sequência), `vehicle` (+history). Índices GiST em geom.
- Validações de cada tarefa OK; relatório com os números vs. os esperados da simulação
  (4107: 23,7 km, 189 stops, 31 carros; stops ~99% com siu).
- **Habilita o Épico 2** (camadas de contexto cruzam com `line_shape`/`stop`).
