# CONS-04 — fechamento medido: snap + cobertura H3 exata → `medido.*` + evento enriquecido (run, 2026-06-11)

> F2 (Bloco 2) · lane **03-consolidator** · quarta task da lane (consome o fechamento por `TripCloseReason` do CONS-03).
> Card: `docs/epicos/bloco2/f2/03-consolidator/CONS-04-fechamento-medido.md` · techspec: `docs/epicos/bloco2/f2/03-consolidator/techspec.md`.
> Replanejado 2026-06-10 (F2-#3/#5/#10) — escreve no schema **`medido`** (substitui o antigo "CONS-04 metricas-saida" que escrevia `core.trip_executed`).
> **Status final: DONE.** (Suíte 93/93 verde, review APROVADO, refute não derrubou — 4 vetores sobreviveram.)

## Cabeçalho do run

| Campo | Valor |
|-------|-------|
| Repo-alvo | `uai-ooh-trip-consolidator` *(repo privado `mddinizbh/uai-ooh-trip-consolidator`)* |
| repoPath | `/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-trip-consolidator` |
| Branch | `feat/ooh-cons-04` |
| Base | `feat/ooh-cons-03` (reconstrução de viagem + linha-do-RT, CONS-03 DONE) |
| Stack | Java 21 / Spring Boot · PostGIS (leitura no `core`, SRID 31983) · h3-java · Spring Kafka · Testcontainers (Postgis + embedded Kafka) |
| Depende de | CONS-03 ✅ + `core.line_shape` (✅ F1, geometria real da **4107**) |
| Pipeline | implement → review → test → validate → refute |

## Counts reais vs. esperado

| Item | Esperado (card/techspec) | Real | OK |
|------|--------------------------|------|----|
| `mvn verify` (Java 21, ms-21.0.10) | BUILD SUCCESS · suíte verde | **BUILD SUCCESS** · **93 testes** · 93 passed / 0 failed / 0 skipped | ✅ |
| Snap 1×/viagem no shape (não por posição) | projeta posições no `core.line_shape` (`ST_LineLocatePoint`/`ST_ClosestPoint`) → progressão fracionária | `PostgisTripMetricsCalculator` faz o snap **uma vez** por viagem fechada; leitura no `core`, SRID 31983 | ✅ |
| **km pelo snap** (não soma de GPS ruidoso) | distância percorrida pela progressão fracionária do snap | km derivado do range de progressão do snap; IT contra a 4107 fechou **km ≈ 12,8 km** (extensão real da linha) | ✅ |
| Completude pela progressão do snap | range do snap / `current_stop_sequence` vs. máx do `pattern_stop` | completude calculada pela fração coberta do shape (não por contagem bruta de pings) | ✅ |
| `v_real` por parada com timestamp **da entity** | snap + ts da entity (não do header — defasagem p95 ~102s, E0) → velocidade em frente a cada parada | `v_real` preenchido por parada usando o timestamp da entity (decisão E0); persistido em `medido.parada_velocidade` | ✅ |
| **Cobertura H3 exata = ping ∪ interpolado** | entre pings o carro atravessa hexágonos sem registro → interpolar o trecho ao longo do shape e emitir hexes intermediários | hexes com ping → `fonte='ping'`; intermediários via `ST_LineInterpolatePoint` ao longo do shape → `fonte='interpolado'`; cobertura on-grid contígua ao corredor | ✅ |
| Gate off-shape (não mentir cobertura em desvio) | ponto longe do shape (> limiar) ⇒ trecho **não** interpolado | gate de **80 m**: trecho com ponto off-shape não é interpolado (provável desvio) — não inventa hex | ✅ |
| `medido.viagem` (1 linha/viagem) | chave única `(vehicle_code, trip_id_rt, service_date, started_at)` + `ON CONFLICT DO NOTHING`; **sem `campaign_id`, sem divergência (F2-#5)** | `JdbcMedidoRepository` persiste 1 linha com a chave única + `ON CONFLICT DO NOTHING` (reprocesso idempotente); fato puro, sem campanha | ✅ |
| `medido.viagem_hex` (grão do recompute, lane 06) | *(viagem × h3_index × faixa_horaria)* com dwell e fonte | persistido com `fonte` (ping/interpolado) e faixa horária — é o grão que a lane 06 (RECAL-01) consome | ✅ |
| `medido.viagem_track` (replay ~1/30s) | track amostrado da viagem | persistido; **não** vai no payload do evento | ✅ |
| `medido.parada_velocidade` | `v_real` por parada | persistido (o pipeline agrega pro modelo) | ✅ |
| Persistência transacional das 4 tabelas `medido` | tudo num fechamento atômico, reprocesso seguro | `JdbcMedidoRepository` grava as 4 tabelas na mesma transação, todas com `ON CONFLICT DO NOTHING` | ✅ |
| Ordem: **persiste `medido` → publica evento** | fato durável primeiro; at-least-once no evento | `MedidoTripClosedSink`: persiste → publica → reset do live no Redis (CONS-05) | ✅ |
| `ooh.trip.completed` **ENRIQUECIDO** (F2-#10) | `viagemId, vehicleCode, lineId, serviceDate, startedAt, endedAt, km, completude, hexesCobertos, faixasHorarias`; **sem track**; consumidor não acessa `medido`/`core` | `KafkaTripCompletedPublisher` emite o payload enriquecido; **sem track**; consumidor (intel cache; CMS no B3) não toca `medido`/`core` | ✅ |
| Estado live zerado após persistir | live da viagem zerado no Redis (CONS-05) pós-persistência | reset disparado no `MedidoTripClosedSink` após o fato durável | ✅ |
| Migração `medido.*` = DDL da techspec | V1 idêntica à DDL especificada | migração V1 confere com a DDL da techspec (4 tabelas + chave única + índices) | ✅ |
| Testes novos | — | unit (`TripMetricsTest`, `OohTripCompletedTest`, `MedidoTripClosedSinkTest`) **8/8** + IT `Cons04FechamentoMedidoIT` (geometria REAL da 4107 em Testcontainers postgis + embedded Kafka) **3/3** | ✅ |

> Os "verdes" são build limpo na validação, não report reaproveitado: o **test** rodou `mvn verify` do zero com Java 21 (`JAVA_HOME=ms-21.0.10`) e fechou em **93/93**. O `implement` parou antes em `compile`/`test-compile` (EXIT=0) e nos unitários CONS-04 (8/8).

## Decisões / desvios

- **Snap 1×/viagem, não por posição.** O cálculo de km/completude/cobertura roda uma única vez no fechamento (`PostgisTripMetricsCalculator`), projetando as posições da viagem no `core.line_shape`. km vem da **progressão fracionária do snap**, não da soma de segmentos GPS ruidosos — é o que dá o ≈ 12,8 km coerente com a extensão real da 4107 no IT.
- **Cobertura H3 exata = `ping` ∪ `interpolado`.** O feed reporta por veículo a cada ~1–2 min, então entre pings o carro atravessa hexágonos sem registro. Os hexes com ping ficam `fonte='ping'`; os intermediários são gerados interpolando o trecho ao longo do shape (`ST_LineInterpolatePoint`) e marcados `fonte='interpolado'`. **Gate off-shape de 80 m:** se um ponto do trecho está longe do shape (> 80 m, provável desvio), o trecho **não** é interpolado — preferimos cobertura faltante a cobertura inventada.
- **`v_real` usa o timestamp da entity, não o do header (E0).** A defasagem p95 do header é ~102 s; usar o ts da entity dá velocidade em frente a cada parada com bem menos viés. Decisão herdada do E0, aplicada na `medido.parada_velocidade`.
- **Fato puro, sem divergência de campanha (F2-#5).** `medido.viagem` não tem `campaign_id` nem comparação linha-rodada × linha-esperada — isso é do módulo OOH do CMS (Bloco 3), via evento. O consolidador só mede.
- **Ordem persiste → publica → reset.** Primeiro o fato durável nas 4 tabelas `medido` (transação única, `ON CONFLICT DO NOTHING` ⇒ reprocesso seguro), depois o evento `ooh.trip.completed` enriquecido (at-least-once), depois o reset do live da viagem no Redis (contrato CONS-05). Reprocessar a mesma viagem não duplica nem o fato nem corrompe o estado.
- **Evento enriquecido fecha o contrato de consumo sem vazar schema (F2-#10).** `ooh.trip.completed` carrega `viagemId, vehicleCode, lineId, serviceDate, startedAt, endedAt, km, completude, hexesCobertos, faixasHorarias` — **sem track**. O consumidor (intel cache agora; CMS no Bloco 3) não acessa `medido`/`core`. Destrava **RECAL-01 (lane 06)** e **RT-02**.
- **Pendência ambiental persistente (toda a lane consolidator):** o repo segue sem `mvnw`/toolchain travando Java 21 — o `mvn` global da Homebrew roda em Java 26 e o pom exige `java.version=21`, então a validação precisa de `JAVA_HOME=<jdk21>` manual. Aberto desde CONS-01; não bloqueou esta entrega.
- **Ruído ambiental do JaCoCo (não é falha de teste):** no estágio `implement` os unitários rodaram com `jacoco.skip=true` porque o agente JaCoCo falha ao instrumentar classes do boot JDK no ambiente (major version 70 — warning de instrumentação, não falha de assert). O `test` (validação) rodou `mvn verify` completo e fechou **93/93** verde.

## Adversarial (o cético — refute)

O refute **não derrubou** (`refuted: false`). Os 4 vetores rodaram e cada um sobreviveu:

1. **"Critério de pronto não batido."** → **NÃO derrubou.** O snap PostGIS calcula km, completude, `v_real` e cobertura H3 (ping ∪ interpolado) em `PostgisTripMetricsCalculator`; persistência transacional das 4 tabelas `medido` com `ON CONFLICT DO NOTHING` em `JdbcMedidoRepository`; ordem persiste → publica → reset em `MedidoTripClosedSink`; evento enriquecido sem track em `KafkaTripCompletedPublisher`; migração V1 idêntica à DDL da techspec. **Sobreviveu.**
2. **"Contrato divergente."** (payload do evento / schema das tabelas fora do card) → **NÃO derrubou.** `ooh.trip.completed` carrega exatamente os campos do F2-#10, sem track; `medido.viagem` sem `campaign_id`/divergência (F2-#5); as 4 tabelas batem com a techspec. **Sobreviveu.**
3. **"Evidência errada de build e testes."** → **NÃO derrubou.** O `test` rodou `mvn verify` do zero com Java 21 (`JAVA_HOME=ms-21.0.10`; `mvn` global em Java 26 quebra o pom, repo sem `mvnw`) e fechou **93/93**; o IT exercita geometria **real** da 4107 em Testcontainers postgis + embedded Kafka (km ≈ 12,8 km, cobertura on-grid, `v_real` preenchido). Não é report reaproveitado do `implement`. **Sobreviveu.**
4. **"Stubs disfarçados."** (cálculo fingido / fixture sintético escondendo o snap) → **NÃO derrubou.** O IT `Cons04FechamentoMedidoIT` roda contra o `core.line_shape` real da 4107, não fixture chapado; o snap, a interpolação e o gate off-shape são exercitados de ponta a ponta no PostGIS. **Sobreviveu.**

Verificação independente (review): veredito **APROVADO** — "CONS-04 está completo e conforme. O fechamento medido implementa exatamente o que a task e a techspec pedem: snap 1×/viagem no `core.line_shape` (SRID 31983, leitura), km/completude pela progressão fracionária do snap (não soma de GPS), cobertura H3 exata (ping ∪ interpolado pelo shape via `ST_LineInterpolatePoint`, com gate off-shape de 80 m para não inventar cobertura em desvio), `v_real` por parada usando o timestamp da entity (E0), persistência das 4 tabelas do `medido`". Checklist confirmado **no código**, não só na justificativa do agente. `validate.ok: true`.

→ **a task sobreviveu** — entrega verde, sem bloqueio aberto de lógica de domínio nem de wiring.

## Estado ao fechar este run

- Fechamento medido na branch `feat/ooh-cons-04`: compila (Java 21), `mvn verify` **93/93**, IT contra a geometria real da **4107** em Testcontainers postgis + embedded Kafka (km ≈ 12,8 km, cobertura ping ∪ interpolado on-grid, `v_real` preenchido, evento `ooh.trip.completed` enriquecido publicado).
- **Não deployado / não em prod** — worker headless; sobe local e nos ITs, sem efeito no banco `ooh` de produção nem no Redis de prod. Migração `medido.*` V1 (Flyway do CONS-01) validada nos ITs, não aplicada em prod.
- **Destrava a jusante:** **RECAL-01 (lane 06)** consome `medido.viagem_hex` (grão do recompute) e **RT-02** consome o evento `ooh.trip.completed` enriquecido. Ambos estavam retidos por CONS-04 — agora liberados.
- **Pendência ambiental aberta:** travar Java 21 (mvnw/toolchain) para não depender de `JAVA_HOME` manual — afeta toda a lane consolidator, segue sem resolução. Ruído de instrumentação do JaCoCo no `implement` é só warning, não falha.
- **Próximo da fila:** CONS-04 era a última peça do fechamento durável; a lane consolidator fica com CONS-05 (acumulador ao vivo, já DONE) como par e a lane 06 (RECAL) liberada para o recompute medido.
