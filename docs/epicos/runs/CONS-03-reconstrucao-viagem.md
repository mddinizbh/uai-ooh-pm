# CONS-03 — reconstrução de viagem + linha-do-RT (run, 2026-06-11)

> F2 (Bloco 2) · lane **03-consolidator** · terceira task da lane (consome o estado por veículo do CONS-02).
> Card: `docs/epicos/bloco2/f2/03-consolidator/CONS-03-reconstrucao-viagem.md` · techspec: `docs/epicos/bloco2/f2/03-consolidator/techspec.md`.
> **Status final: DONE.** (Retomada — a primeira tentativa fechou FAILED/BLOQUEADA por fixture do `core` incompleto + `CoreLineResolver` com construtor ambíguo; ambos os bloqueios foram destravados. Suíte 82/82 verde, review APROVADO, refute não derrubou.)

## Cabeçalho do run

| Campo | Valor |
|-------|-------|
| Repo-alvo | `uai-ooh-trip-consolidator` *(repo privado `mddinizbh/uai-ooh-trip-consolidator`)* |
| repoPath | `/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-trip-consolidator` |
| Branch | `feat/ooh-cons-03` |
| Base | `feat/ooh-cons-02` (consumer + estado por veículo, CONS-02 DONE) |
| Stack | Java 21 / Spring Boot · Spring Kafka · Redis (Lettuce) · h3-java · Testcontainers (Kafka + Postgis + Redis) |
| Depende de | CONS-02 ✅ + `core.trip_pattern`/`core.line` (✅ F1) |
| Pipeline | implement → review → test → validate → refute |

## Counts reais vs. esperado

| Item | Esperado (card/techspec) | Real | OK |
|------|--------------------------|------|----|
| `mvn verify` (Java 21, ms-21.0.10) | BUILD SUCCESS · suíte verde | **BUILD SUCCESS** · **82 testes** · 82 passed / 0 failed / 0 skipped | ✅ |
| 5 condições de fechamento (`TripCloseReason`) | trip_id / reset seq / timeout 10min / último ponto / virada service_date | as 4 por-posição (`TRIP_ID_CHANGED`, `SERVICE_DATE_ROLL`, `SEQUENCE_RESET`, `LAST_STOP`) avaliadas em ordem de prioridade em `DefaultTripCloseDetector.shouldClose`; `TIMEOUT` via `shouldCloseByTimeout` + sweep periódico | ✅ |
| `TripCloseReason` (enum, sem `default`) | tipo finito de rótulo = enum, switch exaustivo sem `default` | enum com os **5 valores** + Javadoc da condição em cada um (convenção uAI) | ✅ |
| Timeout de fechamento por varredura | sweep periódico fecha viagem silenciosa > timeout | `TripTimeoutSweeper.sweep()` `@Scheduled(fixedDelay=timeout-sweep-ms, default 60s)` itera `activeVehicleCodes()` → `reconstructor.closeByTimeout` | ✅ |
| Timeout configurável por env | `TRIP_TIMEOUT_S` (default 10 min) | `application.yml`: `ooh.consolidator.trip.timeout-s: ${TRIP_TIMEOUT_S:600}` injetado no detector via `@Value` | ✅ |
| `service_date` em zona BH (não UTC) | virada de service_date = dia local BH | `serviceDateRolled` computa `LocalDate.ofInstant(..., America/Sao_Paulo)`; **virada de meia-noite UTC NÃO é virada de service_date BH** (`ooh.consolidator.service-date.zone`) | ✅ |
| Linha-do-RT por `route_short_name` | join RT × estático por `core.line.short_name`, **nunca** `gtfs__routes.route_id` | `CoreLineResolver`/`CoreTripPatternCatalog` joinam por `l.short_name` (índice carregado 1× no boot); a ARMADILHA está tratada e documentada no adapter | ✅ |
| Suplementares (S\*) → `padrao_desconhecido` | `line_id` NULL no fato, nunca inventa linha | `CoreLineResolver` resolve prefixo `S*` para `LineMatch.unknownPattern()` **mesmo que o short_name exista** no cadastro | ✅ |
| Viagem parcial fecha com `completude < 1` | carro que aparece no meio não é descartado | `LiveCompletenessRuler` calcula completude pelo `max(n_stops)` do padrão; rota sem padrão (suplementar) → completude desconhecida, fato landa mesmo assim | ✅ |
| `CoreLineResolver` injetável de forma determinística | bean `@Repository` resolvível sem ambiguidade (bug crítico da 1ª tentativa) | construtor de produção é o único de injeção; test-seam vira **construtor package-private** explícito (não compete na resolução do Spring) — **bug crítico destravado** | ✅ |
| `ApplicationContext` sobe nos ITs | fixture semeia o `core` mínimo (`core.line` + `core.trip_pattern` + `core.h3_cell`) | fixture do Testcontainer estendido; **boot sobe** — `ScaffoldBootIT` verde de novo (era a barreira da 1ª tentativa) | ✅ |
| Testes novos | 5 | suíte total **82** (unit por `TripCloseReason` + ITs); cobre as 5 razões, zona BH, S\* → desconhecido, parcial | ✅ |

> Os "verdes" são build limpo na fase de validação: o **test** rodou `mvn verify` do zero com Java 21 (`JAVA_HOME=ms-21.0.10`, fallback do comando da task — o repo não tem `./mvnw`) e fechou em **82/82**. Não é report reaproveitado do `implement`.

## Decisões / desvios

- **Retomada bem-sucedida dos dois bloqueios da 1ª tentativa.** (1) Fixture de Testcontainer agora semeia o `core` mínimo (`core.line` + `core.trip_pattern`, além do `core.h3_cell` já presente) → o `ApplicationContext` sobe e os ITs que exercitam fechamento + atribuição de linha contra o `core` rodam de verdade. (2) `CoreLineResolver` deixou de ter dois construtores competindo: o de produção é o único caminho de injeção e o test-seam é um construtor **package-private** documentado, removendo a ambiguidade de resolução do Spring. As 5 falhas e o bug crítico da run anterior estão fechados.
- **`SERVICE_DATE_ROLL` em zona BH, não UTC.** `serviceDateRolled` usa `America/Sao_Paulo` (`ooh.consolidator.service-date.zone`); cruzar meia-noite UTC **não** dispara virada de service_date. Mesma zona usada pelo `RedisLiveReachStore` (expiry do sketch de alcance do dia) e pelo `ServiceCalendar` — consistente em todo o serviço.
- **Ordem de prioridade explícita das condições por-posição:** `TRIP_ID_CHANGED` → `SERVICE_DATE_ROLL` → `SEQUENCE_RESET` → `LAST_STOP`; a primeira que casa fecha. `TIMEOUT` é a única condição **não-por-posição** — vem do sweep periódico, não do ping.
- **Guarda de TTL documentada (não é defeito, é contrato operacional):** para o sweep de timeout fechar uma viagem silenciosa, o TTL do estado vivo no Redis (`REDIS_STATE_TTL_S`) precisa ser `>=` ao `TRIP_TIMEOUT_S` — senão o estado expira antes do sweep disparar. Nota deixada no Javadoc do `TripTimeoutSweeper`/`LiveStateStore` e ligada à config.
- **`LAST_STOP` conservador:** usa `max(n_stops)` sobre os padrões da linha (limite superior) — nunca fecha cedo. Rota sem padrão resolvível (suplementar / `padrao_desconhecido`) não dispara `LAST_STOP`.
- **`route_id` do feed = `route_short_name` (ARMADILHA F2).** Todo cruzamento RT × estático é por `core.line.short_name`, nunca contra `gtfs__routes.route_id`. A armadilha está tratada e comentada em **todos** os adapters/ports que tocam rota (`CoreLineResolver`, `CoreTripPatternCatalog`, `LineResolver`, `TripPatternCatalog`, `VehiclePosition`, `LiveCompletenessRuler`).
- **Divergência de campanha NÃO entra aqui (F2-#5):** o fato é puro; comparar linha rodada × linha esperada é do módulo OOH do CMS (Bloco 3), via evento.
- **Pendência ambiental persistente:** o repo segue sem `mvnw`/toolchain travando Java 21 — `mvn` default da Homebrew roda em Java mais novo e o pom exige `java.version=21`, então a validação roda com `JAVA_HOME=<jdk21>` manual. Aberto desde CONS-01; não bloqueou esta entrega, mas continua como atrito da lane.

## Adversarial (o cético — refute)

O refute **não derrubou** (`refuted: false`). Diferente da 1ª tentativa — onde o pipeline parou antes e o refute veio `null` — aqui a rodada adversarial rodou e cada vetor sobreviveu:

1. **"Critério de pronto não batido — as 5 condições de fechamento (`TripCloseReason`) estão implementadas?"** → **NÃO derrubou.** As 4 por-posição vivem em `DefaultTripCloseDetector.shouldClose` com ordem de prioridade explícita (`DefaultTripCloseDetector.java:55-92`); `TIMEOUT` é o sweep periódico (`TripTimeoutSweeper.java:48-64`, `@Scheduled`) + `shouldCloseByTimeout`. O enum `TripCloseReason` tem os 5 valores (`TripCloseReason.java:22-38`), cada condição com guarda de null e zona BH. **Sobreviveu.**
2. **"O `CoreLineResolver` é injetável de forma determinística?"** (o bug crítico que derrubou a 1ª tentativa) → **NÃO derrubou.** O construtor de produção é o único caminho de injeção; o test-seam é package-private e não compete na resolução do Spring. **Sobreviveu.**
3. **"As 5 condições e o join por `route_short_name` estão validados de ponta a ponta?"** → **NÃO derrubou.** Com o `core` semeado no fixture, o `ApplicationContext` sobe e os ITs exercitam fechamento + atribuição de linha contra o `core` real (Testcontainers). `S*` → `padrao_desconhecido` e parcial → completude `< 1` cobertos. **Sobreviveu.**

Verificação independente (review): veredito **APROVADO** — "CONS-03 (reconstrução de viagem + linha-do-RT) está completo, conforme à techspec e à task, com a ARMADILHA tratada corretamente e suíte de unidade passando (BUILD SUCCESS em Java 21)". Checklist confirmado **no código, não só na justificativa** do agente: 5 condições de fechamento; join por `short_name`; suplementares para `padrao_desconhecido`; zona BH; sweep agendado. `validate.ok: true`.

→ **a task sobreviveu** — entrega verde, sem bloqueio aberto de lógica de domínio nem de wiring.

## Estado ao fechar este run

- Reconstrução de viagem + linha-do-RT na branch `feat/ooh-cons-03`: compila (Java 21), `mvn verify` **82/82**, `ApplicationContext` sobe, `CoreLineResolver` injetável de forma determinística, ITs exercitando fechamento + atribuição de linha contra o `core` semeado nos Testcontainers.
- **Não deployado / não em prod** — worker headless; sobe local e nos ITs, sem efeito no banco `ooh` nem no Redis de produção.
- **Bloqueios da 1ª tentativa fechados:** fixture do `core` estendido (boot sobe) + construtor ambíguo do `CoreLineResolver` resolvido.
- **Pendência ambiental aberta:** travar Java 21 (mvnw/toolchain) para não depender de `JAVA_HOME` manual — afeta toda a lane consolidator, segue sem resolução.
- **Próximo da fila:** CONS-04 (fechamento de viagem → fato no `medido`), que consome o fechamento por `TripCloseReason` deste run; e CONS-05 (acumulador ao vivo: impressões + alcance HLL). CONS-04 estava **retido** por CONS-03 — agora destravado.
