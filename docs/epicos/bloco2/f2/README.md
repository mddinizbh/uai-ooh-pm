# F2 — Tempo real / verificado (motor de avaliação + acumulador ao vivo, sem comercial)

> **Entrega 2** do vertical uAI-OOH: o **motor verificado** que injeta a trajetória **MEDIDA** na máquina
> face-centric da Onda 1/2. A Onda 1/2 (`uai-ooh-pipeline`) construiu o modelo inteiro — superfície
> `exposure_cell` (H3 × hora), `face_reach`, `line_reach` — com trajetória **estimada** (schedule GTFS);
> o F2 é o cano que produz a trajetória **medida** (GTFS-RT real) e re-deriva o modelo com ela.
> **Leitura obrigatória:** `uai-ooh-pipeline/docs/design/guia-base-para-f2-realtime.md` (com o adendo de 2026-06-10).
> **Decisão de escopo (F2-#1, 2026-06-05): SÓ O MOTOR, ZERO COMERCIAL.** Atribuição por campanha é **Bloco 3**.
> **Replanejado em 2026-06-10** (brainstorm — decisões F2-#3..#10 abaixo). Mapa antigo era pré-face-centric.

## Arquitetura (replanejada 2026-06-10)

```
GTFS-RT (PBH/mobilibus, feed regenera ~15-30s)
  │ poll ~15-20s
  ▼
POLLER ✅ ──► raw.rt__vehicle_position (pouso bruto, particionado/dia, retenção 30d)
  └─────────► Kafka ooh.rt.position (key=vehicle_id, retention 6h)
                  │
                  ▼
          CONSOLIDADOR (stream Java headless — repo novo)
          por posição:   h3(lat,lon) LOCAL (lib H3, sem banco)
                         + estado/acumulado no Redis live:{vehicle}   ← "impressões subindo"
          no fechamento: snap PostGIS no shape → cobertura EXATA
                         grava schema MEDIDO + publica ooh.trip.completed (enriquecido)
                  │
                  ▼
          medido.viagem / viagem_hex / viagem_track / parada_velocidade
                  │
                  ▼ (batch D-1 — pipeline)
          RECOMPUTE: medido × core.exposure_cell × core.od_trip
                     → face_reach/line_reach FONTE MEDIDA → core → serving

INTEL (read-only — é quem o front consulta):
   /realtime/positions  → Redis live:{}        (mapa + acumulado ao vivo)
   /lines/{id}/verified → serving + medido      (verificado, selo ADR-058)

[Bloco 3] módulo OOH do CMS: consome ooh.trip.completed,
          atribui a placement/campanha e persiste NO BANCO DO CMS
```

**Camadas e donos (F2-#3):**

| Camada | O que é | Dono (DDL) |
|---|---|---|
| `raw` | o que a PBH cuspiu (TEXT, auditoria, reproduzível do feed) | pipeline (`rt-raw-ddl`) |
| **`medido`** *(novo)* | **fatos consolidados do mundo real** — viagens, cobertura H3, velocidades | **consolidador (Flyway)** |
| `core` | o **mundo**/modelo — linhas, shapes, hexágonos, OD, face_reach | pipeline |
| `serving` | leitura do app (plano, swap atômico) | pipeline |
| banco do CMS | resultado **comercial** (campanha × placement × entrega) — Bloco 3 | CMS (módulo OOH) |

Teste decisório: *reconstruível só de `raw`+GTFS → `medido`/`core`. Precisa de contrato/tenant → CMS.*

## O que o F2 entrega (sem nenhuma campanha)

- **Viagens medidas por veículo/linha** (`medido.viagem`): nº de viagens, km, completude.
- **Cobertura real da face** (`medido.viagem_hex`): por onde (hexágono H3) e quando (faixa horária) cada veículo passou **de verdade** — o grão que o modelo face-centric consome.
- **Velocidade real por ponto** (`medido.parada_velocidade` → `v_real`).
- **`face_reach`/`line_reach` recomputados com a trajetória medida** (lane 06): a faixa de incerteza estreita — trajetória, frequência e velocidade viram **reais**. **Esse é o produto central do F2.**
- **Mapa ao vivo + acumuladores subindo** (F2-#6): carros se movendo + **impressões** (soma incremental) **e alcance** (HLL com dedup ~±0,8% — RECAL-00/CONS-05; pode **superar o estimado da linha** quando o carro roda fora da rota), escopável por linha.

> ⚠️ **Honestidade (ADR-058):** o RT torna reais **trajetória/frequência/velocidade**. NÃO calibra os
> coeficientes cegos de **visada** (quem efetivamente olha a face) — isso é estudo de campo (Fase C).
> O acumulado ao vivo e o alcance recomputado **continuam estimativa**; selo por métrica, nunca inflar.

## Lanes (= repos)

| Lane | Tipo | Repo | Tasks | Estado |
|---|---|---|---|---|
| [`01-infra/`](01-infra/) | 🟫 INFRA | `uai-infra` + `uai-ooh-pipeline` | INFRA-01..04 | ✅ 01/02 entregues · 🔲 03 (tiering) · 🔲 04 (automação: compose+crons) |
| [`02-poller/`](02-poller/) | 🟨 DATA | `uai-ooh-realtime-poller` | POLL-01..03 | ✅ **entregue** (2026-06-09) |
| [`03-consolidator/`](03-consolidator/) | 🟦 BACK | `uai-ooh-trip-consolidator` *(novo, Java)* | CONS-01..05 | pendente |
| [`04-intel-rt/`](04-intel-rt/) | 🟦 BACK | `uai-ooh-intel` *(estende F1)* | RT-01..03 | pendente |
| [`05-front-rt/`](05-front-rt/) | 🟪 FRONT | `uai-portal` *(estende F1)* | WEB-00..02 | pendente |
| [`06-recalibracao/`](06-recalibracao/) | 🟨 DATA | `uai-ooh-pipeline` | RECAL-00..02 | pendente · RECAL-00 (HLLs) pode rodar já |
| [`07-local/`](07-local/) | 🟫 LOCAL | `uai-infra` + `uai-ooh-realtime-poller` | LOCAL-01..02 | pendente · **valida tudo ANTES do ship** |

Run do que já foi entregue: [`runs/F2-infra-poller-golive.md`](../../runs/F2-infra-poller-golive.md).

## Primitivo tenant-agnóstico (o que mantém sem comercial)

O intel-RT serve **"posição/progresso/acumulado de um conjunto de `vehicle_id`"**. No F2 o conjunto vem
de um **filtro por LINHA** (avaliação); no Bloco 3 viria de uma **campanha** (`CampaignScope`). Mesmo
primitivo, quem chama é diferente → **sem acoplamento comercial agora, sem retrabalho depois**.

## Decisões & gates

**Históricas (mantidas):**
- **F2-#1 (2026-06-05):** motor "frota inteira", sem comercial. CONS roda sem filtro de ativos.
- **F2-#2 (2026-06-06):** transporte = **polling atrás de `PositionFeed` port** (~15s, stateless); SSE/WS a um adapter de distância. **Anti-vazamento:** conjunto de `vehicle_id` **sempre server-derived**.
- **✅ Gate INFRA (2026-06-06):** Kafka (KRaft) + Redis de pé no `uai-infra`.

**Replanejamento 2026-06-10 (brainstorm arquitetura "medido" + face-centric):**
- **F2-#3 — Camadas:** novo schema **`medido`** (fatos do mundo real, dono = consolidador/Flyway); `core` fica **só mundo**. O gancho do guia §4 (`vehicle_trajectory metodo='medida'`) é **substituído** por `medido.viagem_hex`.
- **F2-#4 — Consolidador stream Java headless:** por posição `h3(lat,lon)` local + Redis `live:{vehicle}`; snap PostGIS só no fechamento (cobertura exata interpolada). Sem HTTP além de health. **Não se funde com o intel** (write-path worker ≠ read-path API).
- **F2-#5 — Fato puro:** `campaign_id` e `divergencia_linha` **removidos** do fato. Atribuição/divergência = módulo OOH do CMS (B3), por `vehicle_code` × vigência do placement.
- **F2-#6 — Acumulador ao vivo ENTRA no F2** (era pós-F2): ao vivo = aproximado (hexes com ping); fechamento = exato (interpolado); reconciliam. Selo: estimativa. **Adendo (mesma data):** o ao vivo acumula **impressões** (soma) **e alcance com dedup** (HLL via RECAL-00 — válido porque o carro fora da rota pode alcançar **mais** que o estimado da linha; alcance ao vivo nunca por soma de hexes).
- **F2-#7 — Recompute = pipeline** (ADR-050): lane 06, D-1, mesma fórmula da Onda 2, só troca a fonte da trajetória.
- **F2-#8 — Identidade:** `vehicle_code` = `vehicle.id` do feed (validado no E0 2026-06-09: 85,7–89,2% match); upsert tolerante p/ veículo desconhecido; `rt_vehicle_id` deixa de ser chave de match.
- **F2-#9 — Bloco 3 = módulo OOH no CMS** (não serviço novo; revisa ADR-052). Pré-planejamento em [`../../bloco3/notas-cms-modulo-ooh.md`](../../bloco3/notas-cms-modulo-ooh.md).
- **F2-#10 — Kafka como está:** `ooh.rt.position` + `ooh.vehicle.status` já criados e publicados. `ooh.trip.completed` = **contrato enriquecido** (consumidor futuro não acessa `medido`/`core`).
- **F2-#11 (2026-06-10) — Frota inteira mantida; o custo se resolve com TIERING, não com filtro:** capturar só "linhas de interesse" no poller quebraria a calibração da rede, a reconstrução de viagem (carro troca de linha) e o replay (feed não tem rewind) — e "interesse" é conceito comercial (read-path/B3). Custo real medido: Kafka ~57 msg/s (nada); `raw` ~1,2-1,5 GB/dia → **INFRA-03**: quente 7d no Postgres + frio parquet/zstd no MinIO (~3-4,5 GB/mês). Mesmo padrão pro `medido` depois (quente ~90d).
- **Depende de:** `core` (`trip_pattern`/`line_shape` — ✅ F1) · Onda 1/2 (`exposure_cell`/`od_trip` — ✅) · intel-RT estende o intel F1 (EP2) · front-RT estende o front F1 (EP4).
- **Sequenciamento (LOCAL-FIRST, decisão 2026-06-10):** construir tudo local → E2E local (replay do raw + consolidador local + **disparos MANUAIS dos RECALs**) → validar fixture/4107/reconciliação → **só então** ship pra prod (INFRA-04). Ordem: `gate → jobs ∥ cons → local → e2e → rt ∥ web → contract → ship → verify-prod` — orquestrada pelo workflow **`f2-orchestration`** (`orchestration/README.md`). O ship fica **bloqueado** sem `e2e=validated` no handoff. CONS pode começar já (poller streaming desde 09/jun acumulando raw pro replay).
- **Convenção de repo novo (CONS-01):** `gh repo create` + push de `main` vazia **antes** de qualquer código → regerar do `uai-ooh-service-template`.

## Futuro (pós-F2)

- **trip-updates (F2-#10b, validado 2026-06-10):** o feed `trip-updates` da PBH é 1:1 com o positions (mesmo ts, mesmos veículos) e traz `departure.delay` + próxima parada — âncora de calibração do consolidador (stop_sequence sem inferência geométrica) e proxy de congestionamento (dwell↑ = exposição↑). Capturar é +1 fetch no ciclo do poller. **Pendência pós-F2.**
- **OOH-BACKSEAT (face interna):** modelável como `face_type` com função de audiência própria — embarcados via `line_demand_hourly`/`line_turnover` × viagens **medidas** (o F2 torna o denominador real). Impressões viáveis pós-F2; **alcance único embarcado = gap de dado** (bilhetagem `od_trip` sem `route_id`). Ver notas do Bloco 3.
- **Alertas PBH:** feed `alerts` hoje vazio (0 entities) — vigiar; desvio de linha invalida trip_pattern/exposição.

## Validação

Na linha **4107**: viagens/dia ~ frequência, km ~ extensão×viagens, cobertura H3 plausível vs corredor,
**face_reach medido comparável ao estimado** (o loop), soma incremental ≈ agregado do fechamento.
Counts no banco `ooh` (schema `medido`) via MCP `postgres-ooh`.
