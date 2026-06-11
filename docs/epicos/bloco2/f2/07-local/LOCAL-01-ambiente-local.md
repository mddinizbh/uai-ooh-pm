# LOCAL-01 — ambiente local completo (compose + seed do banco real)

> F2 (Bloco 2) · lane **local** · **Repo-alvo:** `uai-infra` · **Stack:** Docker Compose / pg_dump / psql
> **Criada em 2026-06-10** (estratégia local-first do dono). **Destrava:** LOCAL-02 + parte `e2e`.

## Objetivo
Subir, na máquina local, **tudo que o F2 precisa pra validar ponta a ponta**: Postgres com os dados
reais que o consolidador/RECAL consomem, Kafka com os tópicos, Redis — sem tocar a VPS.

## Como executar
- **`docker-compose.local.yml`** no `uai-infra` (arquivo separado do compose de prod; nunca
  deployado): `postgres-ooh-local` (PostGIS, porta **55432**), `kafka` (KRaft, porta 19092),
  `redis` (porta 16379), `kafka-init` criando `ooh.rt.position` (3 part., 6h), `ooh.vehicle.status`
  (compactado) e `ooh.trip.completed`.
- **Seed (script `local/seed-ooh-local.sh`):** `pg_dump` seletivo do banco real → restore no local:
  - `core`: `line`, `trip_pattern`, `line_shape`, `stop`, `h3_cell`, `exposure_cell`, `od_trip`,
    `vehicle`, `dataset_version` (tabelas que CONS/RECAL leem — schema + dados);
  - `raw.rt__vehicle_position`: **≥2 dias** (partições inteiras — insumo do replay LOCAL-02);
  - `serving`: `line_metrics`/`face_reach`/`line_reach` (pro RECAL comparar estimado vs medido).
- Envs locais documentadas no próprio arquivo (DB_URL local, bootstrap 19092, redis 16379) — são as
  que consolidador local, replay e RECALs manuais usam.
- O handoff do workflow (`orchestration/state.json`) grava host/portas em `lanes.local.env` — os
  estágios de validação das outras lanes leem de lá (nunca validar contra o banco errado).

## Decisões
- **Portas deslocadas** (55432/19092/16379) — não colide com nada local nem sugere prod.
- **Seed por dump, não por pipeline**: objetivo é validar o F2, não re-rodar a Onda 1/2 local.
- `od_trip` (1,2M linhas, ~maior peso) entra inteiro — o RECAL local precisa do reach real.

## Critério de pronto (verificável)
- `docker compose -f docker-compose.local.yml up -d` → todos os serviços saudáveis; tópicos criados.
- Counts do seed batem com a origem (line=303, h3_cell=2615, exposure_cell=188280, od_trip≈1,2M,
  raw ≥2 service_dates) — registrar no run.

## Produz
- docs/epicos/runs/LOCAL-01-ambiente-local.md
