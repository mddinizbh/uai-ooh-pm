# Detalhamento Técnico: lane intel-rt (F2 · realtime read + verificado)

> Techspec da lane **04-intel-rt** (`uai-ooh-intel`, estende o intel do F1). Units = RT-01..03.
> **Reescrita em 2026-06-10** (replanejamento face-centric — F2-#3..#8 no `../README.md`).
> Endpoints novos sobre o Redis live (contrato CONS-05) + `medido` (read-only) + `serving` recomputado (lane 06).
> **Coder não reabre as Decisões abaixo.**

## Contexto
O intel-RT serve **posição ao vivo + acumulado parcial** (do Redis, escrito pelo consolidador) e o
**verificado por linha** (de `medido.viagem` + `face_reach`/`line_reach` fonte medida no serving),
lado a lado com o estimado. Tudo **escopável por linha** (F2), atrás de um primitivo tenant-agnóstico.
Sem PostGIS no read path (ADR-003). O intel **só lê** — quem escreve é o consolidador (`medido`,
Redis) e o pipeline (modelo).

## Decisões Técnicas
- **`PositionFeed` port** (transporte): **polling no F2** (stateless, ~15s); SSE/WS = adapter futuro sob o mesmo contrato (F2-#2).
- **Conjunto SEMPRE server-derived** do escopo autenticado (`RealtimeScope` sealed: F2 = `LineScope`; B3 adiciona `CampaignScope`). O cliente passa a **linha**, nunca `vehicle_code`.
- **Live read = chaves Redis do CONS-05** (`live:vehicle:{}` / `live:line:{}`): o intel não chama o consolidador (headless) nem toca banco no hot path.
- **Verificado lê o que já existe** — `medido` (fatos) + serving (modelo recomputado pela lane 06). O intel **nunca re-deriva** o modelo (ADR-050).
- **Selo de confiança por métrica (ADR-058)** substitui o boolean `aindaEstimativa`: trajetória/frequência/velocidade = MEDIDO; reach/impressões = ESTIMATIVA (coeficientes de visada cegos), com faixa menor.
- **Frescura por evento:** cache do verificado por linha invalidado por `ooh.trip.completed` (payload enriquecido — não precisa consultar `medido` pra invalidar).

## Padrões do Projeto a Seguir
- Estende o intel F1: `JdbcTemplate` read-only, reusa a auth (EP2-07/introspection `uai-auth`). **Records** no domínio; **sealed sem default** (`RealtimeScope`); IDs `String`; sem `ST_*`.
- Adapter Redis novo (lê o contrato CONS-05).

## Riscos Globais
- **Selo honesto:** o reach medido segue estimativa (visada cega) — payload tipado pra não inflar (ADR-058).
- **Acoplamento ao contrato Redis:** mudança nas chaves do CONS-05 quebra o RT-01 — contrato versionado no doc do CONS-05; IT dos dois lados seedam o mesmo formato.
- **Frescura vs custo:** verificado reage ao evento (não recalcula por request).

---

## Unit: RT-01 — realtime read (posição + progresso + acumulado)
- **Contrato**:
```java
record LiveAccumulator(double impressoesParciais, int hexesVisitados, Selo selo) {} // selo=ESTIMATIVA
record LivePosition(String vehicleCode, String lineId, double lat, double lon, Double bearing,
    Integer currentStopSequence, double completudeParcial, LiveAccumulator acumulado, Instant ts) {}

interface QueryRealtimeUseCase { List<LivePosition> positions(RealtimeScope scope); }

@RestController class RealtimeController {
    @GetMapping("/api/realtime/positions") List<LivePosition> byLine(@RequestParam String line);
}

interface LiveStateReader { List<LivePosition> activeOnLine(String lineId); } // live:line:{} → live:vehicle:{}
```
- **Pré-condições**: intel F1 (EP2) + Redis com o contrato CONS-05.
- **Pós-condições**: posições + acumulado da linha; conjunto resolvido no servidor; sem PostGIS.
- **Verificação**: IT com Redis seedado no formato CONS-05; nunca aceita `vehicle_code` do cliente.

## Unit: RT-02 — verificado por linha (medido vs estimado)
- **Contrato**:
```java
enum Selo { MEDIDO, ESTIMATIVA }   // por métrica (ADR-058)
record Metrica(double valor, Selo selo) {}
record VerifiedMetrics(String lineId, Metrica viagensDia, Metrica km, Metrica completudeMedia,
    Metrica velocidadeRealMedia, Metrica reachMedido, Metrica impressoesMedidas,
    EstimatedComparison vsEstimado) {}
record EstimatedComparison(double frequenciaGtfs, double velocidadeAssumida,
    double reachEstimado, double impressoesEstimadas, int faixaPctAntes, int faixaPctDepois) {}

interface VerifiedQueryService { VerifiedMetrics forLine(String lineId); } // medido + serving (lane 06)

@RestController class VerifiedController {
    @GetMapping("/api/lines/{id}/verified") VerifiedMetrics verified(@PathVariable String id);
}

@KafkaListener(topics = "ooh.trip.completed", groupId = "intel-verified")
void onTripCompleted(OohTripCompleted event); // invalida cache da linha (payload enriquecido)
```
- **Pré-condições**: `medido.viagem`/`parada_velocidade` (CONS-04) + serving recomputado (RECAL-01).
- **Pós-condições**: comparação completa com selo por métrica; reach medido marcado ESTIMATIVA (visada cega).
- **Verificação**: IT 4107 com seed de `medido` + serving → medido ≠ estimado, selos corretos.

## Unit: RT-03 — `PositionFeed` port (polling, swappable)
- **Contrato**:
```java
sealed interface RealtimeScope permits LineScope { } // Bloco 3: add CampaignScope
record LineScope(String lineId) implements RealtimeScope {}

interface PositionFeed { List<LivePosition> positionsForScope(RealtimeScope scope); }
// F2: PollingPositionFeed (Redis CONS-05). Futuro: SsePositionFeed/WsPositionFeed, mesmo contrato.
```
- **Pós-condições**: RT-01 consome `PositionFeed`; trocar polling→SSE/WS = novo adapter, sem tocar controller/domínio.
- **Verificação**: teste de contrato; controller depende só da porta.

---

## Diagrama de dependências
```
RT-03 (PositionFeed port) ──> RT-01 (realtime read: Redis live CONS-05)
RT-02 (verificado medido vs estimado) ← medido (CONS-04) + serving (RECAL-01) + evento ooh.trip.completed
```
