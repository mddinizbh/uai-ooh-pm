# CONS-01 — scaffold do `uai-ooh-trip-consolidator`

> F2 (Bloco 2) · lane **consolidator** · **Repo-alvo:** `uai-ooh-trip-consolidator` *(NOVO)* · **Stack:** Java 21 / Spring Boot + Spring Kafka + Redis (+ PostGIS no `core`) · **worker contínuo**
> **Destrava:** CONS-02/03/04.

## Objetivo
Scaffold do consumidor: Kafka consumer + Redis (estado) + acesso ao `core` (PostGIS p/ snap, escrita de `trip_executed`). **Regerar do `uai-ooh-service-template`.**

## Como executar
- **Passo 0 — criar o repo:** `gh repo create mddinizbh/uai-ooh-trip-consolidator --private` + push de uma **`main` vazia** (commit baseline) **antes de qualquer código**. Só então o scaffold (regerar do template).
- Java 21 / Spring Boot, **Spring Kafka** (consumer de `ooh.rt.position`, `group.id`, `key=vehicle_id`), **Redis** (Spring Data Redis/Lettuce) p/ estado por veículo, **datasource `core`** (lê `trip_pattern`/`line_shape`; escreve `trip_executed`). Hexagonal single-module.
- **Flyway (Java)** pras tabelas que o consolidador **grava em runtime** (`trip_executed`, `trip_executed_track`, `pattern_stop_exposure.v_real`) — **regra: dono do DDL = quem grava** (`arquitetura-servicos.md`). Dois donos de migração no `core` (normalizer + consolidator), **tabelas distintas**.

## Decisão
- **PostGIS no consolidador é OK** — o ADR-003 ("sem `ST_*`") vale só pro **intel/serving** (read path); o consolidador opera no `core` (write) e **usa PostGIS** pro snap.

## Critério de pronto
- Sobe, consome o tópico (group configurado), conecta Redis + `core`; Flyway aplica as tabelas do consolidador; healthcheck verde.

## Produz
- docs/epicos/runs/CONS-01-scaffold.md
