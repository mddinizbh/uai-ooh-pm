# Detalhamento Técnico: lane intel-rt (F2 · realtime read + verificado)

> Techspec da lane **04-intel-rt** (`uai-ooh-intel`, estende o intel do F1). Units = RT-01..03.
> Greenfield parcial: o intel F1 nasce no EP2; aqui são **endpoints novos** sobre Redis (estado do consolidador) + `core.trip_executed`.
> **Coder não reabre as Decisões abaixo.**

## Contexto
O intel-RT serve **posição ao vivo** (do Redis) e o **verificado por linha** (de `trip_executed` + `v_real`), incluindo o **alcance recalculado** (fórmula do F1 com a velocidade real). Tudo **escopável por linha** (F2), atrás de um primitivo tenant-agnóstico. Sem PostGIS no read path (ADR-003).

## Decisões Técnicas
- **`PositionFeed` port** (transporte): **polling no F2** (stateless, ~15s); SSE/WS = **adapter futuro** sob o mesmo contrato.
- **Conjunto SEMPRE server-derived** do escopo autenticado (`RealtimeScope`). O cliente passa a **linha**, nunca `vehicle_id`. Anti-vazamento.
- **`RealtimeScope` sealed** (variante carrega dado → sealed, não enum): F2 = `LineScope`; Bloco 3 add `CampaignScope`.
- **RT-02 recalcula o alcance** com `v_real` (fórmula F1 + `serving`), **reagindo a `ooh.trip.completed`**. **Não** o consolidador (que fica só com fatos). Recalibração de `model_params` = normalizer (fora daqui).
- **Alcance recalculado = AGREGADO por linha** (read-time). O **acumulador por-posição ao vivo** é pós-F2 (módulo de stream separado) — ver Evolução.

## Padrões do Projeto a Seguir
- Estende o intel F1: `JdbcTemplate` no `serving`/`core` (read-only), reusa a auth (EP2-07, modo stub). **Records** no domínio; **sealed sem default** (`RealtimeScope`); IDs `String`; sem `ST_*`.
- Adapter de Redis novo (lê o estado que o consolidador grava).

## Riscos Globais
- **Alcance recalculado segue ESTIMATIVA** (só velocidade vira real; coeficientes não calibrados) → marcar sempre como estimativa, não "medido".
- **Consistência** entre o agregado (RT-02) e o futuro acumulador incremental → **mesma fórmula** (fonte única) pra reconciliar.
- **Frescura vs custo:** reagir a `ooh.trip.completed` (não recalcular por request) — cache do verificado por linha, invalidado pelo evento.

---

## Unit: RT-01 — realtime read (posição + progresso)
- **Responsabilidade**: posição ao vivo + progresso p/ um conjunto **server-derived** (linha), lendo do Redis.
- **Localização**: `adapter/in/web/RealtimeController`, `application/port/in/QueryRealtimeUseCase`, `adapter/out/redis/VehicleStateReader`, `domain/LivePosition`.
- **Contrato**:
```java
record LivePosition(String vehicleId, String lineId, double lat, double lon, Double bearing,
    Integer currentStopSequence, double completudeParcial, Instant ts) {}

interface QueryRealtimeUseCase { List<LivePosition> positions(RealtimeScope scope); }

@RestController class RealtimeController {
    @GetMapping("/api/realtime/positions") List<LivePosition> byLine(@RequestParam String line);
}

interface VehicleStateReader { List<LivePosition> activeOnLine(String lineId); } // resolve o conjunto no Redis
```
- **Pré-condições**: intel F1 (EP2) + Redis com estado do consolidador (CONS-02).
- **Pós-condições**: `GET positions?line=` devolve os carros da linha ao vivo; conjunto resolvido no servidor; sem PostGIS.
- **Decisões locais**: o `lineId` mapeia p/ `vehicle_id`s via `current_trip_id→route_id` no Redis.
- **Verificação**: IT com Redis seedado (Testcontainers) → posições da linha; nunca aceita `vehicle_id` do cliente.

## Unit: RT-02 — verificado + alcance recalculado
- **Responsabilidade**: métricas verificadas por linha + **alcance recalculado** (fórmula F1 com `v_real`) + comparação com o estimado.
- **Localização**: `adapter/in/web/VerifiedController`, `application/VerifiedReachService`, `adapter/in/kafka/TripCompletedConsumer`, `domain/{VerifiedMetrics,ReachRecompute,EstimatedComparison}`.
- **Contrato**:
```java
record EstimatedComparison(double frequenciaGtfs, double velocidadeAssumida, int faixaPct) {}
record ReachRecompute(double impressoesReais, double alcanceReais, boolean aindaEstimativa) {} // aindaEstimativa=true sempre no F2
record VerifiedMetrics(String lineId, int viagensDia, double km, double completudeMedia,
    double velocidadeRealMedia, ReachRecompute alcance, EstimatedComparison vsEstimado) {}

interface VerifiedReachService { VerifiedMetrics forLine(String lineId); } // junta trip_executed + v_real + serving (fórmula F1)

@RestController class VerifiedController {
    @GetMapping("/api/lines/{id}/verified") VerifiedMetrics verified(@PathVariable String id);
}

@KafkaListener(topics = "ooh.trip.completed", groupId = "intel-verified")
void onTripCompleted(OohTripCompleted event); // invalida cache do verificado da linha
```
- **Pré-condições**: `core.trip_executed` + `pattern_stop_exposure.v_real` (CONS-04) + `serving.line_metrics` (F1).
- **Pós-condições**: `GET verified?line=` → números reais + alcance recalculado (com `aindaEstimativa=true`) + comparação; atualiza ao receber `ooh.trip.completed`.
- **Decisões locais**: **mesma fórmula de alcance do F1** (fonte única, pra reconciliar com o acumulador futuro). Cache por linha invalidado pelo evento.
- **Riscos**: estimativa (ver global) — marcar.
- **Verificação**: IT 4107 (Testcontainers seed de `trip_executed`/`v_real`) → alcance recalculado com `v_real` ≠ estimado; flag `aindaEstimativa`.

## Unit: RT-03 — `PositionFeed` port (polling, swappable)
- **Responsabilidade**: encapsular o transporte do realtime atrás de uma porta — polling no F2, SSE/WS = adapter futuro.
- **Localização**: `application/port/out/PositionFeed`, `domain/RealtimeScope`, `adapter/out/PollingPositionFeed`.
- **Contrato**:
```java
sealed interface RealtimeScope permits LineScope { } // Bloco 3: add CampaignScope
record LineScope(String lineId) implements RealtimeScope {}

interface PositionFeed { List<LivePosition> positionsForScope(RealtimeScope scope); } // conjunto server-derived
// F2: PollingPositionFeed (Redis query). Futuro: SsePositionFeed/WsPositionFeed, mesmo contrato.
```
- **Pré-condições**: RT-01.
- **Pós-condições**: o RT-01 consome `PositionFeed`; trocar polling→SSE/WS = novo adapter, **sem tocar controller/domínio**.
- **Decisões locais**: `RealtimeScope` sealed (exaustivo, sem default).
- **Verificação**: teste de contrato do `PositionFeed`; o controller depende só da porta.

---

## Evolução futura (pós-F2) — acumulador de alcance ao vivo
**Não é unit do F2.** Módulo de **stream separado** que consome `ooh.rt.position`, calcula por posição a **contribuição incremental** de alcance (`v_real` local × corredor/embarque **cacheado** × coef), **acumula por viagem em Redis** e expõe "alcance acumulado ao vivo"; no fim, **total = soma**. Usa a **mesma fórmula** do `VerifiedReachService` (RT-02) → **reconcilia** (soma incremental ≈ agregado). **Continua estimativa.** Mantém consolidador e intel-hot-path limpos.

## Diagrama de dependências
```
RT-03 (PositionFeed port) ──> RT-01 (realtime read)
RT-02 (verificado + alcance recalculado)  ← consome ooh.trip.completed (CONS-04)
   └─ usa a fórmula F1 (serving) — fonte única reusada pelo acumulador futuro
```
