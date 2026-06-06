# Detalhamento Técnico: lane consolidator (F2 · reconstrução de viagem)

> Techspec da lane **03-consolidator** (`uai-ooh-trip-consolidator`, Java 21/Spring). Units = os cards CONS-01..04.
> Greenfield: especificado a partir dos cards + `epico-5-realtime-consolidado.md` + `arquitetura-servicos.md` (sem codebase ainda).
> **Coder não reabre as Decisões abaixo.**

## Contexto
O consolidador consome `ooh.rt.position` (frota inteira), mantém estado por veículo em Redis, **reconstrói viagens** (sequência contígua `(vehicle_id, trip_id)`), atribui a **linha do RT** e, ao fechar a viagem, calcula **km / completude / velocidade por ponto** (snap no shape, PostGIS no `core`), persistindo `core.trip_executed` (+ track + `v_real`) e emitindo `ooh.trip.completed`. É a saída **verificada** que calibra o F1. **Sem comercial** (`campaign_id` NULL, modo frota inteira).

## Decisões Técnicas
- **Estado por veículo em Redis**, chave `veh:{vehicleId}` (hash). Motivo: estado mutável de alta frequência, fora do Postgres. Alternativa rejeitada: estado no Postgres (escrita por posição cara).
- **Ordem garantida pela partição** (`key=vehicle_id`, INFRA-01). O consumer **assume ordem por veículo**; não reordena. Alternativa rejeitada: buffer de reordenação (complexidade sem ganho, a partição já ordena).
- **Idempotência por `feed_timestamp`**: posição com `feed_timestamp ≤ state.lastFeedTs` é **ignorada** (absorve o at-least-once do poller).
- **Fechamento por 5 condições** (CONS-03) + **timeout 10min** (varredura periódica de estados ociosos).
- **Linha sempre do RT** (`trip_id→route_id`), nunca MCO. 3,3% suplementares → `padrao_desconhecido` (fallback route+dir+geo).
- **Snap no fechamento** (1× por viagem), não por posição. Motivo: PostGIS pesado; ao fechar basta. **PostGIS é permitido** (consolidador opera no `core` write; ADR-003 restringe só intel/serving).
- **Flyway (Java) é dono** das tabelas que o consolidador grava (`trip_executed`, `trip_executed_track`, `pattern_stop_exposure.v_real`). Regra: dono do DDL = quem grava.
- **Track downsampled ~1/30s**.

## Padrões do Projeto a Seguir
- Hexagonal single-module (`domain/` records puros · `application/port` in/out · `adapter/in/kafka` · `adapter/out/{redis,persistence,kafka}` · `config`).
- **Records** no domínio; **enum** p/ tipos finitos de rótulo (`TripCloseReason`); **sealed sem default** onde a variante carrega dado. IDs como `String`.
- Spring Kafka (`@KafkaListener`), Spring Data Redis (Lettuce), `JdbcTemplate`/Flyway no `core`.
- Scaffold: criar repo via `gh` + `main` vazia → regerar do `uai-ooh-service-template`.

## Riscos Globais
- **Ordem depende da partição**: se `key≠vehicle_id` ou rebalance partir um veículo entre partições, a ordem quebra. Mitigação: contrato fixo `key=vehicle_id` (INFRA-01) + 1 consumer por partição.
- **Viagem que nunca fecha** (carro some sem último ponto): mitigação = timeout 10min (varredura). Calibrar.
- **Match de linha dos 3,3% supl**: fallback geo pode errar. Mitigação: marcar `padrao_desconhecido`, não inventar linha.
- **Dois donos de Flyway no `core`** (normalizer + consolidator): tabelas distintas; coordenar baseline/histórico pra não colidir.
- **Replay/reprocesso**: at-least-once + idempotência por `feed_timestamp` cobre duplicata; reprocesso de viagem fechada precisa de chave única (`vehicle_id, trip_id, service_date, started_at`).

---

## Unit: CONS-01 — scaffold + config
- **Responsabilidade**: app Spring Boot com consumer Kafka, Redis, datasource `core`, Flyway das tabelas próprias.
- **Localização**: `uai-ooh-trip-consolidator` (repo novo) · `config/`, `Application.java`.
- **Contrato**:
```java
@SpringBootApplication
class TripConsolidatorApplication { /* main */ }

@Configuration class KafkaConfig { /* consumer factory: key=String(vehicleId), value=VehiclePosition */ }
@Configuration class RedisConfig { /* template p/ VehicleState */ }
@Configuration class CoreDataSourceConfig { /* JdbcTemplate no core + Flyway das tabelas do consolidador */ }
```
- **Pré-condições**: INFRA-01 (tópico+Redis), `uai-ooh-service-template`.
- **Pós-condições**: app sobe, consome o tópico, conecta Redis+core, Flyway aplica `trip_executed*`.
- **Dependências**: nenhuma unit anterior.
- **Decisões locais**: Flyway baseline coexistindo com o do normalizer (tabelas distintas).
- **Verificação**: healthcheck verde; Flyway migra em Testcontainers `postgis`.

## Unit: CONS-02 — consumo + estado por veículo
- **Responsabilidade**: receber posições (em ordem por veículo), manter `VehicleState` em Redis, absorver duplicatas.
- **Localização**: `adapter/in/kafka/PositionListener`, `adapter/out/redis/VehicleStateStore`, `domain/VehicleState`, `domain/VehiclePosition`.
- **Contrato**:
```java
record VehiclePosition(String vehicleId, String tripId, String routeId,
    double lat, double lon, Double bearing, Integer currentStopSequence, Instant feedTimestamp) {}

record VehicleState(String vehicleId, String currentTripId, int currentStopSequence,
    Instant startedAt, Instant lastFeedTs, double lastLat, double lastLon, int trackPointCount) {}

interface VehicleStateStore {                 // port out (Redis)
    Optional<VehicleState> get(String vehicleId);
    void put(VehicleState state);
    void evict(String vehicleId);
}

@KafkaListener(topics = "ooh.rt.position", groupId = "trip-consolidator")
void onPosition(VehiclePosition position);    // adapter in
```
- **Pré-condições**: CONS-01.
- **Pós-condições**: estado por veículo atualizado em ordem; posição com `feedTimestamp ≤ lastFeedTs` ignorada.
- **Decisões locais**: hash Redis por `vehicleId`; TTL de segurança p/ veículos que somem.
- **Riscos**: ordem (ver global).
- **Verificação**: IT com embedded Kafka + Redis (Testcontainers) — sequência de posições → estado correto; duplicata ignorada.

## Unit: CONS-03 — reconstrução de viagem + linha-do-RT
- **Responsabilidade**: detectar fechamento de viagem e resolver a linha (do RT).
- **Localização**: `application/TripReconstructor`, `domain/TripCloseDetector`, `application/LineResolver`, `domain/{CompletedTrip,LineMatch,TripCloseReason}`.
- **Contrato**:
```java
enum TripCloseReason { TRIP_ID_CHANGED, SEQUENCE_RESET, TIMEOUT, LAST_STOP, SERVICE_DATE_ROLL }

interface TripCloseDetector {
    Optional<TripCloseReason> shouldClose(VehicleState current, VehiclePosition incoming);
    Optional<TripCloseReason> shouldCloseByTimeout(VehicleState current, Instant now); // varredura
}

record LineMatch(String lineId, String patternId, boolean suplementar) {} // suplementar => padrao_desconhecido
interface LineResolver { LineMatch resolve(String tripId, String routeId, double lat, double lon); }

record CompletedTrip(String vehicleId, LineMatch line, LocalDate serviceDate,
    Instant startedAt, Instant endedAt, List<VehiclePosition> track, TripCloseReason reason) {}

interface TripReconstructor { Optional<CompletedTrip> apply(VehicleState state, VehiclePosition incoming); }
```
- **Pré-condições**: CONS-02 (estado) + `core.trip_pattern`/`core.line` (F1).
- **Pós-condições**: viagem fecha nas 5 condições; linha do RT (96,7%) ou `padrao_desconhecido` (3,3%); parcial fecha com completude < 100%.
- **Decisões locais**: timeout = 10min; fallback geo = shape mais próximo dentro do `route+direction`.
- **Riscos**: viagem-que-não-fecha; match supl (ver global).
- **Verificação**: testes de unidade do `TripCloseDetector` por cada `TripCloseReason`; IT da 4107 → nº viagens/dia ~ frequência.

## Unit: CONS-04 — métricas + saída (`trip_executed`)
- **Responsabilidade**: ao fechar, calcular km/completude/velocidade (snap) e persistir + emitir evento.
- **Localização**: `application/TripMetricsCalculator` (PostGIS), `adapter/out/persistence/TripExecutedRepository`, `adapter/out/kafka/TripCompletedPublisher`, `domain/{TripExecuted,TripMetrics,StopSpeed}`.
- **Contrato**:
```java
record StopSpeed(String stopId, int stopSequence, double vReal) {}
record TripMetrics(double km, double completude, int pontosServidos, List<StopSpeed> speeds) {}
interface TripMetricsCalculator { TripMetrics compute(CompletedTrip trip); } // snap no shape via PostGIS no core

record TripExecuted(String vehicleId, String lineId, String patternId, LocalDate serviceDate,
    Instant inicio, Instant fim, Duration duracao, double km, int pontosServidos,
    double completude, String campaignId, String divergenciaLinha) {} // campaignId/divergencia = null no F2

interface TripExecutedRepository {            // escreve core (Flyway dono)
    void save(TripExecuted trip, List<VehiclePosition> downsampledTrack, List<StopSpeed> speeds);
}

record OohTripCompleted(String tripExecutedId, String lineId, LocalDate serviceDate) {}
interface TripCompletedPublisher { void publish(OohTripCompleted event); }
```
- **Pré-condições**: CONS-03 (`CompletedTrip`).
- **Pós-condições**: `trip_executed` + `trip_executed_track` (downsample ~1/30s) + `pattern_stop_exposure.v_real` gravados; `ooh.trip.completed` emitido; chave única `(vehicle_id, trip_id, service_date, started_at)` evita duplicar.
- **Decisões locais**: snap 1×/viagem; `campaignId` NULL.
- **Riscos**: performance do snap (mitiga: por viagem, não por posição); reprocesso (chave única).
- **Verificação**: IT 4107 no banco real (MCP `postgres-ooh`) → km ~ extensão×viagens, `v_real` preenchido; **alimenta RT-02 (loop verificado→estimado)**.

---

## Diagrama de dependências
```
CONS-01 (scaffold)
   └─> CONS-02 (consumo+estado Redis)
          └─> CONS-03 (reconstrução + linha-do-RT)
                 └─> CONS-04 (snap/métricas → trip_executed + evento)
```
