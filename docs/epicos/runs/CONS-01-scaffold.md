# CONS-01 — scaffold do `uai-ooh-trip-consolidator` + Flyway do `medido` (run, 2026-06-11)

> F2 (Bloco 2) · lane **03-consolidator** · primeira task da lane (destrava CONS-02..05).
> Card: `docs/epicos/bloco2/f2/03-consolidator/CONS-01-scaffold.md` · techspec: `docs/epicos/bloco2/f2/03-consolidator/techspec.md`.
> **Status final: DONE.**

## Cabeçalho do run

| Campo | Valor |
|-------|-------|
| Repo-alvo | `uai-ooh-trip-consolidator` *(NOVO — repo privado `mddinizbh/uai-ooh-trip-consolidator`)* |
| repoPath | `/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-trip-consolidator` |
| Branch | `feat/ooh-cons-01` |
| Base | `main` vazia (baseline só com `README.md`, "Initial commit", repo criado 2026-06-04) |
| Stack | Java 21 / Spring Boot · Spring Kafka · Redis (Lettuce) · `JdbcTemplate` · Flyway · h3-java 4.1.1 · jackson-datatype-jsr310 2.17.3 |
| Pipeline | implement → review → test → validate → refute |

## Counts reais vs. esperado

| Item | Esperado (card/techspec) | Real | OK |
|------|--------------------------|------|----|
| Passo 0 — repo privado + `main` vazia antes do código | 1 baseline limpa | repo já existia (private, 2026-06-04), `main` = só `README.md` | ✅ |
| Tabelas no schema `medido` (Flyway `V1`) | `viagem`, `viagem_hex`, `viagem_track`, `parada_velocidade` | 4 (mesmas) | ✅ |
| Migration no classpath | `V1__medido_schema.sql` em `db/migration` | presente em `target/classes/db/migration/` | ✅ |
| `core` migrado pelo consolidador | 0 (acesso só leitura) | 0 — `CREATE SCHEMA IF NOT EXISTS medido` apenas | ✅ |
| Estrutura hexagonal single-module | `domain` · `application/port` · `adapter/{in/kafka,out/redis,out/persistence,out/kafka}` · `config` | pacotes criados (records `VehiclePosition`/`HealthState` + `KafkaConfig`/`RedisConfig`/`OohDataSourceConfig` + package-infos por adapter) | ✅ |
| `mvn -DskipTests test-compile` (Java 21) | EXIT=0, 0 erros | EXIT=0, 0 erros | ✅ |
| Testes novos | 2 | 2 (`ScaffoldBootIT`, `HealthStateTest`) | ✅ |
| `mvn clean verify` (test) | BUILD SUCCESS · ITs verdes · Flyway aplica em Testcontainers `postgis` | BUILD SUCCESS · 3/3 testes (0 falhas) · Flyway cria `medido` + aplica `V1` em `postgis/postgis:16-3.4` | ✅ |
| Gate de cobertura JaCoCo | ≥ 80% | "All coverage checks have been met." | ✅ |

> Nuance entre estágios: o **implement** parou no `test-compile` por instrução (suíte não executada nessa fase); o **test** rodou `mvn clean verify` fresh e o **refute** re-rodou `mvn clean verify` do zero — ambos BUILD SUCCESS. O "verde" reportado não é report antigo reaproveitado: é build limpo na fase de validação.

## Decisões / desvios

- **Build sem `mvnw` no repo.** O scaffold não trouxe Maven Wrapper; teste/refute usaram o Maven do Homebrew (fallback previsto na task). Pendência menor: avaliar commitar `mvnw` pra travar versão do Maven (não bloqueia CONS-01).
- **PostGIS em leitura é OK** (confirmado pela techspec): snap/cálculo roda em query no `core`; ADR-003 ("sem `ST_*`") segue valendo só pro intel/serving. Os ITs sobem `postgis/postgis:16-3.4` via Testcontainers — Flyway exercitado na mesma família de imagem do prod.
- **`h3-java` 4.1.1** já no `pom` (lat/lng → célula H3 local, sem round-trip no banco) — antecipa CONS-02 (acúmulo por hex). Inofensivo no scaffold.
- **Decisão local adiada** (particionamento mensal de `viagem_hex`/`viagem_track` por `service_date` e janelas de retenção do tiering INFRA-03): **não fechada nesta task** — depende de volume real e não bloqueia o scaffold. Fica explícita pra CONS-02+.
- **Identidade de veículo** (`vehicle.id` do feed = `core.vehicle.vehicle_code`, decisão F2-#8) herdada do INFRA/POLL; o consumer já configura `key=vehicle_code` em `ooh.rt.position`, `group.id=trip-consolidator`.

## Adversarial (o cético — refute)

O refute não derrubou. `refuted: false`. O que ele tentou:

1. **"Critério de pronto não bate AGORA — não confie em report antigo."**
   Rodou `mvn clean verify` fresh. Resultado: **BUILD SUCCESS**. Log mostra Flyway criando schema `medido` e aplicando `V1` na imagem `postgis/postgis:16-3.4` (Testcontainers). `ScaffoldBootIT`: 2 testes, 0 falhas (valida as 4 tabelas do `medido` + `actuator/health` UP). `HealthStateTest`: 1/0. JaCoCo: "All coverage checks have been met." → **sobreviveu**.

2. **"Contrato divergente — Kafka/Redis/Flyway/DDL implementados diferem do card/techspec."**
   Conferência item a item: 4 tabelas do `medido` batem com o card (`viagem`, `viagem_hex`, `viagem_track`, `parada_velocidade`); `core` sem migração (só leitura); consumer `ooh.rt.position`/`group.id=trip-consolidator`/`key=vehicle_code`; estrutura hexagonal single-module conforme. Sem divergência de contrato no scaffold → **sobreviveu**.

Verificação independente do review (compile reproduzido fora do implement): `mvn -DskipTests test-compile` EXIT=0 com Java 21, `V1__medido_schema.sql` no classpath, `jackson-datatype-jsr310 2.17.3` e `h3-4.1.1` presentes. Veredito: **APROVADO**.

## Estado ao fechar este run

- Scaffold do `uai-ooh-trip-consolidator` na branch `feat/ooh-cons-01`: compila (Java 21), `mvn clean verify` verde, **Flyway dono do `medido`** com as 4 tabelas aplicadas em Testcontainers `postgis`, cobertura ≥ 80%.
- **Não deployado / não em prod** — é scaffold de worker headless; sobe local e nos ITs. Sem efeito no banco `ooh` de produção (o `medido` real será criado quando o serviço subir contra o ambiente).
- **Próximo da fila:** CONS-02 (acúmulo por hex / lógica de consolidação de viagem) — destravada. Fechar as decisões locais adiadas (particionamento mensal por `service_date` + janelas de tiering) com volume real.
