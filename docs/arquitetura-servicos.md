# uAI OOH — Arquitetura de serviços (F1→F3)

> **Visão platform-wide (toda a plataforma uAI):** [`arquitetura-plataforma-uai.md`](arquitetura-plataforma-uai.md).
> Este doc é o **deep-dive do vertical OOH**.
>
> Doc de referência da decomposição em microsserviços do vertical OOH (inteligência + metrificação
> de mídia em ônibus de BH). Complementa `plano-normalizacao-core.md` (o `core`) e os `epicos/*`.
> Vertical `uai-ooh-*` — **distinto** do `uai-midia-core` (agente de *criação* de arte).

## Princípios (plataforma uAI)
VPS único + Docker Compose, Nginx, GHCR, deploy centralizado no `uai-infra` (repos nunca tocam o
VPS), Postgres/Redis/**Kafka KRaft**/MinIO em 127.0.0.1. Hexagonal single-module por serviço; sealed
classes; deploy só por GitHub Actions; service-to-service via `/internal/**` + `X-UAI-Internal-Key`;
auth delegada ao `uai-auth` (introspection); `tenant_id`+RLS só onde há dado de tenant.

## Dois planos
- **Plano de DADOS / referência OOH** (sem tenant): linhas, métricas, score, realtime, consolidado.
  Vertical `uai-ooh-*`, evolui o `uai-bus-lines-map`.
- **Plano COMERCIAL** (tenant): clientes, campanhas, cobrança — gerido pelo **`uai-cms` revisado**
  (gerenciador **multi-vertical** de campanhas da empresa; `campaign.type` = marketing | ooh | …),
  que **orquestra** os backends do vertical. cms cresce **generalizando** (lifecycle comum) e
  **delegando** os specifics por tipo — não embutindo lógica OOH.

## Mapa de serviços
| Serviço | Plano | Papel | Stack | Tenant | Porta |
|---|---|---|---|---|---|
| `uai-ooh-pipeline` | dados | **Um repo, dois módulos** do CLI `python -m ooh_pipeline`: `ingestor/` (GTFS/censo/OSM/PBH → `raw`, agendado) + `normalizer/` (`raw`→`core` PostGIS + materializa flat; **dono do schema `core`**). Compartilham `db.py`/DSN/toolchain. Separável em 2 repos só se o Bloco 2 exigir cadência própria do ingestor. | Python + PostGIS | não | jobs |
| `uai-ooh-realtime-poller` | dados | Polling GTFS-RT (~15–20s) → Kafka `ooh.rt.position` + landing particionado. Contínuo. | Python (protobuf) | não | worker |
| `uai-ooh-trip-consolidator` | dados | Consome `ooh.rt.position`, **filtra carros ativos**, reconstrói viagens (snap no shape) → `core.trip_executed` (+ traçado), atribui à campanha; emite `ooh.trip.completed`. Calcula velocidade por ponto. | Java/Spring (Spring Kafka + Redis) | não | worker |
| `uai-ooh-intel` | dados | API de referência: catálogo/score/ficha (F1) + realtime read (posições/progresso p/ um conjunto de vehicle_id) + alcance verificado. **Serviço NOVO (do `uai-ooh-service-template`); o `uai-bus-lines-map` fica congelado.** Sem PostGIS — lê o schema `serving` (tabelas planas) no `ooh-postgis` + Redis. | Java/Spring | não | :8085 |
| `uai-cms` (revisado) | comercial | Gerenciador multi-vertical: cliente/campanha(`type`)/cobrança genéricos + edge do frontend. `type=ooh` → delega ao OOH. | Java/Spring | sim | :8081 |
| `uai-ooh-commercial` | comercial | Specifics OOH: carros vendidos por campanha, **ativação/"start" + registro de carros ativos**, relatório de entrega; publica `ooh.vehicle.activated/deactivated`. Orquestrado pelo cms. | Java/Spring | sim | :8087 |

**Frontend = projeto separado** → via `uai-cms` revisado (agrega o plano de dados OOH por
`/internal/**`; delega auth ao `uai-auth`). Nenhum serviço backend embute SPA.

## Eventos (Kafka, tópicos OOH dedicados)
- `ooh.rt.position` — lote de posições cru (poller → consolidator).
- `ooh.trip.completed` — viagem consolidada fechada (consolidator → métricas/relatório).
- `ooh.vehicle.activated` / `ooh.vehicle.deactivated` — start/stop de carro na campanha (comercial →
  consolidator), **dirige o tracking**.
- `ooh.dataset.refreshed` — normalizer virou nova versão `core`.
> *(Confirmar adição vs. a regra dos "5 tópicos genéricos" da plataforma — OOH é vertical novo.)*

## Dados
- **`ooh` DB (PostGIS):** `raw` (landing; `rt__vehicle_position` **particionado por dia** + retenção)
  + `core` (normalizado + `trip_executed`/`trip_executed_track` + métricas materializadas).
- **Redis:** última posição por veículo + estado de viagem em andamento + conjunto de carros ativos.
- **Serving (ADR-003, sem PostGIS):** `intel` lê tabelas planas; nenhum `ST_*` em runtime.
- **Comercial (tenant):** tabelas em `uai` com `tenant_id` + RLS. Cruzamento **campanha↔carro↔viagem**
  por **`vehicle_code`** (correlação) + `campaign_id` carimbado no `trip_executed` + agregação de entrega
  via `intel` — **sem cross-DB** (ADR-052).

## Fluxo de ativação e tracking ("dar start no carro")
1. Comercial (cms→`uai-ooh-commercial`) marca carro V **ACTIVE** na campanha C (mídia plotada) →
   publica `ooh.vehicle.activated`.
2. `trip-consolidator` adiciona V ao conjunto ativo (Redis) e passa a consolidar as viagens de V,
   **usando a LINHA do RT** (`trip_id`→`route_id`, **nunca** o histórico MCO — carros mudam de linha),
   atribuindo à campanha e **sinalizando divergência** se a linha ≠ a esperada (alerta de execução).
3. Mapa ao vivo (frontend → cms) pede ao `intel` as posições dos carros **ativos daquela
   campanha/tenant** (o cms fornece o conjunto; o `intel` é tenant-agnóstico).
4. Carro sem START não é rastreado; ENDED sai do conjunto. O poller continua landando a frota
   inteira no `raw` (auditoria/F1), mas só os ativos viram `trip_executed`/realtime.

## Índices & performance (cada épico inclui na DDL)
- GiST em todo `geom_31983` (`stop`, `line_shape`, `trip_pattern`, `census_sector`, `poi`,
  `road_segment`) → KNN, `ST_DWithin`, `ST_Intersects`.
- btree nas chaves de junção (`line.short_name`, `census_sector.cd_setor`, `stop.gtfs_stop_id`/`siu`,
  `pattern_stop(pattern_id)`, `pattern_stop_exposure(pattern_id,stop_id)`, `poi(categoria)`).
- RT: **partição diária** + btree `(vehicle_id,trip_id,timestamp)` + índice por `vehicle_id`.
- Consolidado: `trip_executed(vehicle_id,service_date)`, `(line_short_name,service_date)`,`(campaign_id)`.
- Serving flat: btree `(line_id,dataset_version)`. Comercial: `(tenant_id,…)` + RLS.
- **Princípio:** cruzamento espacial pesado é precomputado pelo normalizer; serving só faz SELECT indexado.

### Índices na `raw` (para a normalização) — convenção
A `raw` é landing zone (tudo TEXT, **sem índice por design**), mas a normalização roda queries pesadas
sobre ela (KNN, `ST_DWithin`, joins, agregações em `mco` 815k, `stop_times` ~6,2M, `censo` 5,1k×3,
`ponto_onibus` 70k, `trecho_logradouro` 55k). Cada tarefa de normalização **cria, ao rodar, os índices
que precisa na `raw`** — convenção (padrões já no código do `normalizer`):
1. **Geometria (WKT em TEXT):** NÃO indexar a raw. Materializar a geom numa **temp-table com GiST**
   (`CREATE TEMP TABLE _x ON COMMIT DROP AS SELECT ST_…; CREATE INDEX ON _x USING gist(geom)`) — padrão
   do `build_core_stop`. Descartável, não polui a raw, resolve KNN/`ST_DWithin`.
2. **Chaves de junção (btree):** `CREATE INDEX IF NOT EXISTS … ` + `ANALYZE` no início da tarefa, e
   **deixar** (idempotente, barato, acelera re-runs) — padrão do `build_core_trip_pattern`.
3. **Persistência:** manter `INSERT…SELECT` server-side (normalizer) e `COPY` (ingestor) — não trazem
   dados pro Python, não precisam de chunk. **Quando uma tarefa processar volume NO PYTHON**, reusar a
   **classe genérica de persistência em chunks do pipeline** (não reimplementar).

Índices na `raw` por tarefa (criados quando o épico respectivo roda):
| Tarefa | Tabela raw | Índice | Tipo |
|---|---|---|---|
| 1.2 stop | `pbh__ponto_onibus` | geom (temp-table) | GiST temp |
| 1.3/1.4 pattern | `gtfs__stop_times`,`gtfs__shapes`,`gtfs__trips` | `trip_id`,`shape_id`,`route_id` +ANALYZE | btree |
| 1.6 vehicle | `pbh__mco_consolidado` | `(veiculo)` +ANALYZE | btree |
| 2.1 census | `censo__setores_bh`,`agregados_basico_bh`,`renda_responsavel_bh` | `(cd_setor)` ×3 +ANALYZE | btree |
| 2.1/3.1 geo censo | `censo__setores_bh` | geom (temp-table) | GiST temp |
| 2.3/3.3 arterial | `pbh__trecho_logradouro` | geom (temp-table) | GiST temp |
| 2.2 poi | `overture/fsq/osm__poi` | geom (temp-table, se precisar) | GiST temp |
| 3.4 demand | `pbh__mco_consolidado` | `(linha,tipo_dia)` +ANALYZE | btree |

## Roadmap de construção
- **Bloco 1 — F1 Planejamento:** `uai-ooh-pipeline` (módulos ingestor + normalizer) + `intel` + tela catálogo/score/ficha.
  Conteúdo = Épicos 0–4.
- **Bloco 2 — F2 Tempo real:** `realtime-poller` + Kafka + `trip-consolidator` + realtime no `intel`
  + Redis + mapa ao vivo. **Pré-req:** registro mínimo de carros ativos (versão enxuta do comercial OOH).
  Detalhe: `epicos/epico-5-realtime-consolidado.md`.
- **Bloco 3 — F3 Comercial:** revisão do `uai-cms` (multi-vertical) + `uai-ooh-commercial` + telas
  (frontend separado) + assinatura. Detalhe: `epicos/epico-6-comercial.md`.

## Alcance — resumo do modelo (detalhe em `plano-normalizacao-core.md`)
Externo = **E_pontos** (gente nos pontos do trajeto via embarque, modulado por velocidade de
passagem; verificado usa a velocidade real do consolidator) + **E_pop_frontagem** (residentes ~30m,
coef pequeno) + **E_traf** (trânsito, v2). Interno = **E_pax** (passageiros, MCO). Impressões/OTS,
não pessoas únicas; coeficientes não calibrados → **ranking/score vendável, absoluto ilustrativo**.
Proxies e premissas: ver `proxies-e-premissas.md`.

## Regras-âncora
- `intel` herda ADR-003 (sem PostGIS no serving) e ADR-004 (dado de referência sem tenant).
- `normalizer` é dono do schema `core` (migrations próprias).
- Comercial (`uai-cms`/`uai-ooh-commercial`) com tenant_id + RLS.
- Fronteira dados↔comercial (ADR-052): correlação por **`vehicle_code`**, `campaign_id` como token no
  `trip_executed`, relatório por **composição via `intel`** — **sem cross-DB join**.
- Deploy: container→GHCR→`uai-infra` compose; nunca tocar VPS direto.
