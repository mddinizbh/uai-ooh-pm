# CONS-02 — consumo `ooh.rt.position` + estado por veículo (Redis) + h3 local (run, 2026-06-11)

> F2 (Bloco 2) · lane **03-consolidator** · segunda task da lane (consome o scaffold do CONS-01).
> Card: `docs/epicos/bloco2/f2/03-consolidator/CONS-02-consumo-estado.md` · techspec: `docs/epicos/bloco2/f2/03-consolidator/techspec.md`.
> Contrato de chaves Redis: `docs/epicos/bloco2/f2/03-consolidator/CONS-05-acumulador-ao-vivo.md`.
> **Status final: DONE.**

## Cabeçalho do run

| Campo | Valor |
|-------|-------|
| Repo-alvo | `uai-ooh-trip-consolidator` *(repo privado `mddinizbh/uai-ooh-trip-consolidator`)* |
| repoPath | `/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-trip-consolidator` |
| Branch | `feat/ooh-cons-02` |
| Base | `feat/ooh-cons-01` (scaffold + Flyway do `medido`, CONS-01 DONE) |
| Stack | Java 21 / Spring Boot · Spring Kafka · Redis (Lettuce) · h3-java 4.1.1 · Testcontainers (Kafka + Postgis + Redis) |
| Depende de | CONS-01 ✅ + INFRA-01 ✅ (tópico `ooh.rt.position`, key=`vehicle_id`) |
| Pipeline | implement → review → test → validate → refute |

## Counts reais vs. esperado

| Item | Esperado (card/techspec) | Real | OK |
|------|--------------------------|------|----|
| Consumer Kafka keyed | `@KafkaListener` em `ooh.rt.position`, key=`vehicle_code` → mesma partição por veículo → ordem temporal | consumer keyed por `vehicleCode`, ack **manual** (KafkaConfig herdado do CONS-01) → ordem por partição garantida | ✅ |
| Contrato de chaves Redis (CONS-05) | `HASH live:vehicle:{code}` + `SET live:vehicle:{code}:hexes` + índice `live:line:{lineId}` | mesmas chaves; `put` pipelinado em **1 round-trip** (leitor nunca vê estado parcial) + TTL de segurança | ✅ |
| Idempotência (at-least-once do poller) | ignora `feed_timestamp ≤ last_feed_ts` | `VehicleState.isDuplicate` trata `<=` (igual também é duplicata) | ✅ |
| H3 local (F2-#4) | `latLngToCell(lat, lon, 9)` via h3-java, O(1), sem banco; hex do ping no set; off-grid não acumula | `H3Indexer.resolveOnGrid` (2.615 h3 carregados no boot); off-grid avança estado mas **não** adiciona hex | ✅ |
| Identidade (F2-#8) | `vehicle_code` = `vehicle.id` do feed, sem join com `core.vehicle` | direto; veículo desconhecido processa normal | ✅ |
| `mvn -DskipTests compile` | EXIT=0 | EXIT=0 | ✅ |
| `mvn verify` (Java 21, ms-21.0.10) | BUILD SUCCESS · ITs verdes · JaCoCo ≥ 80% | BUILD SUCCESS · **18 unit + 5 ITs verdes** · JaCoCo ≥ 80% atingido | ✅ |
| `PositionConsumerIT` (embedded Kafka + Redis + Postgis Testcontainers) | IT do ciclo completo | **3/3** (inclui dedup `duplicateFeedTimestampIsAbsorbed` + off-grid sem hex) | ✅ |
| `ScaffoldBootIT` (herdado CONS-01) | boot verde | **2/2** | ✅ |
| Testes novos | 3 | 3 criados (esperado 3) | ✅ |
| Total da suíte | — | **23 testes · 23 passed · 0 failed · 0 skipped** | ✅ |

> Os "verdes" são build limpo na fase de validação: o **implement** parou no `mvn -DskipTests compile` (EXIT=0) por instrução; **test** e **refute** rodaram `mvn verify` com `JAVA_HOME=21` do zero. Não é report reaproveitado.

## Decisões / desvios

- **⚠️ Maven default usa Java 26 do Homebrew, que quebra a build.** O `java.version=21` do pom + Mockito inline não rodam sob Java 26. **Mitigação adotada:** testes desenhados **sem Mockito** (fakes), e a validação roda com `JAVA_HOME=<jdk21>` (ms-21.0.10). Pendência ambiental para a lane inteira — não bloqueia CONS-02, mas o `mvnw`/toolchain travando Java 21 segue como item aberto do CONS-01.
- **Sem `mvnw` no repo** (herdado do CONS-01): o comando do test/refute foi `JAVA_HOME=<jdk21> mvn verify` (fallback previsto). Avaliar commitar Maven Wrapper + toolchain para fixar a versão.
- **`KafkaConfig` herdado do CONS-01**: ack manual e `group.id=trip-consolidator` reaproveitados; CONS-02 só adiciona o `@KafkaListener` e a lógica de estado.
- **Estado escrito em 1 round-trip (pipeline)**: o `HASH` + `SET de hexes` + índice de linha sobem juntos, então o leitor (intel/RT-01, CONS-05) **nunca observa estado parcial**. Decisão consistente com "consolidador é a única escrita".
- **Off-grid mantém track, não acumula hex** (F2-#4): posição fora dos 2.615 hexes de `core.h3_cell` avança `VehicleState` (posição/ts/contadores) mas não entra no set de hexes — coberto por `offGridPositionAdvancesStateButAddsNoHex`.
- **Acúmulo de impressões/alcance (HLL) NÃO entra aqui** — é CONS-05. O CONS-02 entrega o estado por veículo + set de hexes visitados que o CONS-05 consome.

## Adversarial (o cético — refute)

O refute **não derrubou**. `refuted: false`. Vetor que ele tentou:

1. **"Critério de pronto não batido — relê o card/techspec e confronta cada item contra o código."**
   Confrontou ordem, dedup, hex no set e IT (embedded Kafka + Redis). Resultado: **todos cobertos**.
   - **Ordem:** consumer keyed por `vehicleCode` → mesma partição → ordem temporal; idempotência por `feed_timestamp` (`VehicleState.isDuplicate` trata `<= lastFeedTs`).
   - **Dedup:** comprovado em unit (`equalFeedTimestampIsAlsoTreatedAsDuplicate`, `isDuplicateIsTrueForEqualOrOlderFeedTimestamp`) **e** em IT (`duplicateFeedTimestampIsAbsorbed`).
   - **Hex no set:** ping do `H3Indexer.resolveOnGrid` entra no set; off-grid não acumula (`offGridPositionAdvancesStateButAddsNoHex`).
   - **IT real:** `PositionConsumerIT` 3/3 com embedded Kafka + Redis + Postgis Testcontainers (ciclo completo, não mock).
   → **sobreviveu.**

Verificação independente (review): veredito **APROVADO** — "CONS-02 está conforme e verificado de forma independente". Os 4 itens do checklist cumpridos: (1) consumer keyed por `vehicle_code` com `@KafkaListener` em `ooh.rt.position` + ack manual (ordem por partição); (2) `VehicleState` no Redis conforme contrato CONS-05 — `HASH live:vehicle:{code}`, `SET live:vehicle:{code}:hexes` e índice `live:line:{lineId}`, TTL de segurança, `put` pipelinado em único round-trip; (3) idempotência por `feed_timestamp`; (4) h3 local com off-grid sem acúmulo. `validate.ok: true` (sem checks pendentes).

## Estado ao fechar este run

- Consumer de `ooh.rt.position` + estado por veículo em Redis na branch `feat/ooh-cons-02`: compila (Java 21), `mvn verify` verde (23/23), JaCoCo ≥ 80%, `PositionConsumerIT` exercitando embedded Kafka + Redis + Postgis Testcontainers.
- **Não deployado / não em prod** — worker headless, sobe local e nos ITs; sem efeito no banco `ooh` ou no Redis de produção.
- **Pendência ambiental aberta:** travar Java 21 (mvnw/toolchain) para não depender de `JAVA_HOME` manual — afeta toda a lane consolidator.
- **Próximo da fila:** CONS-03 (reconstrução de viagem / fechamento por timeout) e CONS-05 (acumulador ao vivo: impressões + alcance HLL), ambos consumindo o `VehicleState` + set de hexes deste run. CONS-05 também depende de RECAL-00.
