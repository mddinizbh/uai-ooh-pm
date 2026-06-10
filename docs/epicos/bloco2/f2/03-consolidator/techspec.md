# Detalhamento Técnico: lane consolidator (F2 · reconstrução de viagem + cobertura H3 + ao vivo)

> Techspec da lane **03-consolidator** (`uai-ooh-trip-consolidator`, Java 21/Spring). Units = CONS-01..05.
> **Reescrita em 2026-06-10** (replanejamento face-centric — decisões F2-#3..#8 no `../README.md`).
> Greenfield. Contratos em Java (records/interfaces — sem implementação). **Coder não reabre as Decisões.**

## Contexto

O consolidador consome `ooh.rt.position` (frota inteira), mantém **estado + acumulado ao vivo** por
veículo em Redis, **reconstrói viagens** e, ao fechar cada viagem, calcula **km / completude /
velocidade por ponto / cobertura H3 exata** (snap no shape, PostGIS) e persiste no **schema `medido`**
(fatos do mundo real — F2-#3), emitindo **`ooh.trip.completed` enriquecido**. É a fonte MEDIDA que a
lane 06 injeta no modelo face-centric (`face_reach`/`line_reach`). **Sem comercial**: fato puro, sem
`campaign_id`/divergência (F2-#5 — atribuição é do módulo OOH do CMS, Bloco 3).

## Decisões Técnicas

- **Escreve no schema `medido`, nunca no `core`** (F2-#3). Flyway (Java) é **dono do schema `medido`
  inteiro** — elimina o risco "dois donos de Flyway no core". O `core` é tocado só em **leitura**
  (`line`, `trip_pattern`, `line_shape`, `h3_cell`).
- **H3 por posição é LOCAL** (lib `h3-java`): `latLngToCell(lat, lon, 9)` — O(1), sem banco. Os
  **2.615 hexágonos** de `core.h3_cell` são carregados em memória no boot (set de h3_index; posição
  fora da grade = hex ignorado no acumulado, posição mantida no track).
- **Estado por veículo em Redis** com **contrato público** (CONS-05): o intel-RT lê as mesmas chaves
  (posições ao vivo + acumulado parcial). Motivo: estado mutável de alta frequência fora do Postgres,
  e o live read não pode depender de API do consolidador (que é headless).
- **Ordem garantida pela partição** (`key=vehicle_id`, INFRA-01); o consumer assume ordem por veículo.
- **Idempotência por `feed_timestamp`**: posição com `feed_timestamp ≤ state.lastFeedTs` é ignorada
  (absorve o at-least-once do poller).
- **Identidade (F2-#8):** `vehicle_code` = `vehicle.id` do feed, **direto** (decisão E0 2026-06-09).
  Sem join com `core.vehicle` no hot path; veículo fora do cadastro **não é erro** (frota
  suplementar/renovação) — o fato landa com o `vehicle_code` do feed.
- **Fechamento por 5 condições** + **timeout 10min** (varredura periódica). **Linha sempre do RT**
  (`route_id` → `core.line.short_name`); suplementares (S*) → `padrao_desconhecido` (line_id NULL +
  fallback geo opcional).
- ⚠️ **ARMADILHA — o `route_id` do feed NÃO é o `route_id` do GTFS estático.** O campo se chama
  `route_id` no protobuf, mas o **valor** é o `route_short_name` (número público: `101`, `9101`,
  `SC01A`...). Todo cruzamento RT × estático é por **`route_short_name`** (`core.line.short_name`) —
  **nunca** contra `gtfs__routes.route_id`. Idem `trip_id`: o do RT não existe no GTFS estático
  (junção é sempre por rota). Validado no E0 (2026-06-09).
- **Snap no fechamento** (1× por viagem, PostGIS no `core` em leitura, SRID **31983**): km,
  completude, v_real por parada e **cobertura H3 exata** — interpola o trajeto pelo shape entre
  pings (o feed reporta a cada ~1–2min por veículo; sem interpolação a cobertura tem buracos).
- **Ao vivo = aproximado, fechamento = exato (F2-#6):** o acumulado em Redis usa só hexes com ping
  (`fonte='ping'`); a cobertura persistida completa com `fonte='interpolado'`. Os dois reconciliam
  (mesma régua); o número ao vivo carrega selo de **estimativa** (ADR-058).
- **Headless:** sem HTTP além de health/metrics. **Não se funde com o intel** (F2-#4): write-path
  worker (escala com a frota) ≠ read-path API (escala com usuários).
- **Track downsampled ~1/30s** (na prática ≈ todos os pings — cadência do feed é maior que 30s).

## Schema `medido` (Flyway do consolidador — DDL de referência)

```sql
CREATE SCHEMA IF NOT EXISTS medido;

CREATE TABLE medido.viagem (
  viagem_id       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  vehicle_code    text NOT NULL,              -- = vehicle.id do feed (F2-#8)
  trip_id_rt      text NOT NULL,              -- trip_id cru do RT
  line_id         text,                       -- do RT (route_id→core.line); NULL = padrao_desconhecido
  pattern_id      text,
  service_date    date NOT NULL,
  started_at      timestamptz NOT NULL,
  ended_at        timestamptz NOT NULL,
  close_reason    text NOT NULL,              -- TripCloseReason
  km              numeric,
  completude      numeric,                    -- 0..1 (parcial fecha com < 1)
  pontos_servidos int,
  UNIQUE (vehicle_code, trip_id_rt, service_date, started_at)   -- reprocesso seguro
);

CREATE TABLE medido.viagem_hex (               -- A COBERTURA (grão que o recompute consome, F2-#3)
  viagem_id     uuid NOT NULL REFERENCES medido.viagem ON DELETE CASCADE,
  h3_index      text NOT NULL,                -- res 9 (core.h3_cell)
  faixa_horaria smallint NOT NULL,            -- 0..23
  entrou_em     timestamptz,
  saiu_em       timestamptz,
  dwell_s       numeric,
  fonte         text NOT NULL,                -- 'ping' | 'interpolado'
  PRIMARY KEY (viagem_id, h3_index, faixa_horaria)
);

CREATE TABLE medido.viagem_track (             -- replay (~1/30s)
  viagem_id uuid NOT NULL REFERENCES medido.viagem ON DELETE CASCADE,
  seq int NOT NULL, ts timestamptz NOT NULL, lat double precision, lon double precision,
  PRIMARY KEY (viagem_id, seq)
);

CREATE TABLE medido.parada_velocidade (        -- v_real; o pipeline agrega pro modelo (lane 06)
  viagem_id uuid NOT NULL REFERENCES medido.viagem ON DELETE CASCADE,
  stop_id text NOT NULL, stop_sequence int NOT NULL, v_real numeric,
  PRIMARY KEY (viagem_id, stop_sequence)
);
```

*Decisão local do CONS-01:* particionar `viagem_hex`/`viagem_track` por mês de `service_date` se o
volume pedir (~26k viagens/dia × ~50 hexes ≈ 1,3M linhas/dia em viagem_hex) + política de retenção
do `medido` (o agregado vive no modelo; o fato granular pode ter janela — definir com dado real).

## Fixture de validação — os 5 carros de teste (decisão do dono, 2026-06-10)

Toda validação do consolidador (ITs com dado real + RECAL-02) usa estes veículos como referência —
1 por consórcio (1º dígito) + 1 extra:

| vehicle_code | Consórcio | Visto no feed em 2026-06-10 |
|---|---|---|
| `11198` | 1 | ✅ linha 9501 (9 trips no dia) |
| `20736` | 2 | ⚠️ ausente hoje — útil pro caso "carro some/aparece" (timeout/parcial) |
| `30835` | 3 | ✅ linhas 2101/2150 (**troca de linha no dia** — testa TRIP_ID_CHANGED entre linhas) |
| `40705` | 4 | ✅ linha 9801 |
| `40806` | 4 | ✅ linha 3054 |

Critério: pra cada carro presente, viagens fecham com nº/dia plausível, km ~ extensão×viagens da
linha vista, cobertura H3 contígua ao corredor; o `30835` valida a atribuição quando o mesmo carro
roda 2 linhas; o `20736` valida timeout/ausência sem erro. (A 4107 segue como referência de LINHA.)

## Riscos Globais

- **Ordem depende da partição**: contrato fixo `key=vehicle_id` + 1 consumer por partição.
- **Viagem que nunca fecha**: timeout 10min (varredura). Calibrar com dado real.
- **Interpolação errada em desvio de rota**: se o carro saiu do shape, interpolar pelo shape mente.
  Mitigação: distância ponto→shape acima de limiar ⇒ trecho não interpolado (só pings) + flag.
- **Volume do `viagem_hex`**: ver decisão de particionamento acima.
- **Replay/reprocesso**: chave única de `viagem` + `ON CONFLICT DO NOTHING`.

---

## Unit: CONS-01 — scaffold + config + Flyway do `medido`
- **Responsabilidade**: app Spring Boot com consumer Kafka, Redis, datasource (write `medido`, read `core`), Flyway do schema `medido`.
- **Localização**: repo novo `uai-ooh-trip-consolidator` (template `uai-ooh-service-template`; convenção `gh repo create` + `main` vazia).
- **Contrato**:
```java
@SpringBootApplication class TripConsolidatorApplication { }
@Configuration class KafkaConfig { /* consumer: key=String(vehicleCode), value=VehiclePosition */ }
@Configuration class RedisConfig { /* live state — contrato CONS-05 */ }
@Configuration class OohDataSourceConfig { /* JdbcTemplate; Flyway schema=medido */ }
```
- **Pré-condições**: INFRA-01 ✅, template.
- **Pós-condições**: sobe, consome o tópico, conecta Redis + banco; Flyway cria o schema `medido`.
- **Verificação**: healthcheck verde; Flyway migra em Testcontainers `postgis`.

## Unit: CONS-02 — consumo + estado/acumulado por veículo
- **Responsabilidade**: receber posições em ordem, manter `VehicleState` (com hexes/acumulado) no Redis, absorver duplicatas.
- **Contrato**:
```java
record VehiclePosition(String vehicleCode, String tripId, String routeId,
    double lat, double lon, Double bearing, Integer currentStopSequence, Instant feedTimestamp) {}

record VehicleState(String vehicleCode, String currentTripId, String routeId, int currentStopSequence,
    Instant startedAt, Instant lastFeedTs, double lastLat, double lastLon,
    Set<String> hexesPing, double impressoesParciais, int trackPointCount) {}

interface LiveStateStore {                       // port out (Redis, chaves do CONS-05)
    Optional<VehicleState> get(String vehicleCode);
    void put(VehicleState state);                 // mantém também live:line:{lineId} (índice p/ RT-01)
    void evict(String vehicleCode);
}

@KafkaListener(topics = "ooh.rt.position", groupId = "trip-consolidator")
void onPosition(VehiclePosition position);
```
- **Pós-condições**: estado atualizado em ordem; `feedTimestamp ≤ lastFeedTs` ignorado; hex do ping
  (h3 local) adicionado ao set; TTL de segurança p/ veículos que somem.
- **Verificação**: IT embedded Kafka + Redis — sequência → estado correto; duplicata ignorada.

## Unit: CONS-03 — reconstrução de viagem + linha-do-RT
- **Responsabilidade**: detectar fechamento e resolver a linha (do RT, nunca MCO).
- **Contrato**:
```java
enum TripCloseReason { TRIP_ID_CHANGED, SEQUENCE_RESET, TIMEOUT, LAST_STOP, SERVICE_DATE_ROLL }

interface TripCloseDetector {
    Optional<TripCloseReason> shouldClose(VehicleState current, VehiclePosition incoming);
    Optional<TripCloseReason> shouldCloseByTimeout(VehicleState current, Instant now);
}

record LineMatch(String lineId, String patternId, boolean padraoDesconhecido) {}
interface LineResolver { LineMatch resolve(String tripId, String routeId, double lat, double lon); }

record CompletedTrip(String vehicleCode, LineMatch line, LocalDate serviceDate,
    Instant startedAt, Instant endedAt, List<VehiclePosition> track, TripCloseReason reason) {}
```
- **Pós-condições**: fecha nas 5 condições; linha do RT (96,7%) ou `padrao_desconhecido` (3,3% supl);
  parcial fecha com completude < 1; veículo fora do cadastro processa normal (F2-#8).
- **Verificação**: unidade por `TripCloseReason`; IT 4107 → viagens/dia ~ frequência.

## Unit: CONS-04 — fechamento: snap + cobertura exata + persistência + evento
- **Responsabilidade**: ao fechar, calcular métricas e **cobertura H3 exata** (interpolada pelo shape) e persistir no `medido` + publicar evento enriquecido.
- **Contrato**:
```java
enum FonteCobertura { PING, INTERPOLADO }
record HexVisit(String h3Index, int faixaHoraria, Instant entrouEm, Instant saiuEm,
    double dwellS, FonteCobertura fonte) {}
record StopSpeed(String stopId, int stopSequence, double vReal) {}
record TripMetrics(double km, double completude, int pontosServidos,
    List<StopSpeed> speeds, List<HexVisit> cobertura) {}

interface TripMetricsCalculator { TripMetrics compute(CompletedTrip trip); }
// snap ST_LineLocatePoint/ST_ClosestPoint no core.line_shape (31983, leitura);
// interpola o trecho entre pings pelo shape → hexes intermediários (fonte=INTERPOLADO);
// ponto longe do shape (> limiar) ⇒ trecho não interpolado.

interface MedidoRepository {                      // escreve medido (Flyway dono)
    UUID save(CompletedTrip trip, TripMetrics metrics);   // ON CONFLICT (chave única) DO NOTHING
}

record OohTripCompleted(String viagemId, String vehicleCode, String lineId, LocalDate serviceDate,
    Instant startedAt, Instant endedAt, double km, double completude,
    int hexesCobertos, List<Integer> faixasHorarias) {}   // ENRIQUECIDO (F2-#10): consumidor não acessa medido
interface TripCompletedPublisher { void publish(OohTripCompleted event); }
```
- **Pós-condições**: `medido.viagem` + `viagem_hex` (ping∪interpolado) + `viagem_track` +
  `parada_velocidade` gravados; evento publicado; **sem `campaign_id`/divergência** (F2-#5);
  estado live da viagem zerado no Redis.
- **Verificação**: IT 4107 no banco real (MCP `postgres-ooh`) → km ~ extensão×viagens, cobertura
  contígua ao corredor, `v_real` preenchido. **→ alimenta a lane 06 (recompute).**

## Unit: CONS-05 — acumulador ao vivo (contrato Redis lido pelo intel)
- **Responsabilidade**: manter, por posição, o **acumulado parcial da viagem aberta** e o índice por
  linha — o contrato que o RT-01 lê. (F2-#6 — entrou no escopo do F2.)
- **Contrato (chaves Redis):**
```
live:vehicle:{vehicleCode}   HASH  lat, lon, bearing, lineId, tripId, currentStopSequence,
                                   completudeParcial, impressoesParciais, hexesVisitados, ts
                                   TTL 5min (some do mapa se o feed parar)
live:line:{lineId}           SET   vehicleCodes ativos na linha agora (índice p/ conjunto
                                   server-derived do RT-01) — TTL renovado por update
```
- **Acumulado:** `impressoesParciais += exposure(hex, tipoDia, faixa) × coef` quando a posição entra
  num hex **novo** da viagem (cache de `core.exposure_cell` em memória no boot; **mesma régua** que o
  recompute da lane 06 — fonte única da fórmula pra reconciliar). Selo: **estimativa** (ADR-058).
- **Pós-condições**: intel lê posição+acumulado **sem tocar o consolidador**; fechou a viagem →
  acumulado zera (o exato foi pro `medido`).
- **Verificação**: IT — sequência de posições → hash/sets corretos; reconciliação
  |Σ incremental − agregado do fechamento| pequena na 4107.

---

## Diagrama de dependências

```
CONS-01 (scaffold + Flyway medido)
   └─> CONS-02 (consumo + estado/hex ping + idempotência)
          ├─> CONS-03 (fechamento + linha-do-RT)
          │      └─> CONS-04 (snap + cobertura exata → medido + evento)
          └─> CONS-05 (acumulado ao vivo — contrato lido pelo RT-01)
```
