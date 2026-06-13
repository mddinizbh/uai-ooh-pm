# CONS-01 — scaffold do `uai-ooh-trip-consolidator` + Flyway do `medido`

> F2 (Bloco 2) · lane **consolidator** · **Repo-alvo:** `uai-ooh-trip-consolidator` *(NOVO)* · **Stack:** Java 21 / Spring Boot + Spring Kafka + Redis + h3-java (+ PostGIS no `core` em **leitura**) · **worker contínuo headless**
> **Destrava:** CONS-02..05. · *(Replanejado 2026-06-10 — F2-#3)*

## Objetivo
Scaffold do consumidor: Kafka consumer + Redis (estado/live) + datasource (escreve **`medido`**, lê `core`) + **Flyway dono do schema `medido`**. **Regerar do `uai-ooh-service-template`.**

## Como executar
- **Passo 0 — criar o repo:** `gh repo create mddinizbh/uai-ooh-trip-consolidator --private` + push de uma **`main` vazia** (baseline) **antes** de qualquer código. Só então o scaffold (regerar do template).
- Java 21 / Spring Boot, **Spring Kafka** (consumer `ooh.rt.position`, `group.id=trip-consolidator`, key=`vehicle_code`), **Redis** (Lettuce), datasource único no banco `ooh` (`JdbcTemplate`).
- **Flyway cria e é dono do schema `medido`**: `viagem`, `viagem_hex`, `viagem_track`, `parada_velocidade` (DDL de referência na [techspec](techspec.md)). Regra: **dono do DDL = quem grava**. O `core` é acessado **só em leitura** (`line`, `trip_pattern`, `line_shape`, `h3_cell`) — zero migração no `core`.
- Hexagonal single-module (`domain/` records · `application/port` · `adapter/{in/kafka,out/redis,out/persistence,out/kafka}` · `config`). Sem HTTP além de health/metrics.

## Decisões
- **PostGIS no consolidador é OK em leitura/cálculo** (snap roda em query no `core`); ADR-003 ("sem `ST_*`") segue valendo só pro intel/serving.
- **Decisão local a tomar na execução:** particionamento mensal de `viagem_hex`/`viagem_track` por `service_date`; retenção do `medido` segue o **padrão de tiering da INFRA-03** (quente ~90d no Postgres → parquet/zstd no MinIO; `medido.viagem` fica pra sempre) — calibrar janelas com volume real.

## Critério de pronto
- Sobe, consome o tópico, conecta Redis + banco; Flyway aplica o schema `medido`; healthcheck verde; Flyway migra em Testcontainers `postgis`.

## Produz
- docs/epicos/runs/CONS-01-scaffold.md
