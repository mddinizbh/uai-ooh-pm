# INFRA-01 — Kafka tópico `ooh.rt.position` + Redis — ✅ ENTREGUE (2026-06-09)

> F2 (Bloco 2) · lane **infra** · **Repo-alvo:** `uai-infra` · **Stack:** Docker Compose / Kafka KRaft / Redis
> **✅ Entregue:** `kafka-init` no compose cria `ooh.rt.position` (3 part., retention 6h) **e** `ooh.vehicle.status`
> (compactado, key=`vehicle_code` — bônus além do planejado). Commits `f122424`/`f9b0510`; poller no compose com
> `depends_on: kafka-init` (`d9a6cd9`). Run: [`F2-infra-poller-golive.md`](../../../runs/F2-infra-poller-golive.md).
> **Destrava:** POLL (publica) + CONS (consome + estado no Redis).

## Objetivo
Criar o tópico **`ooh.rt.position`** no Kafka existente e confirmar o **Redis** acessível pro consolidador. Sem provisionar broker novo.

## Como executar
- **Tópico `ooh.rt.position`:**
  - **chave de partição = `vehicle_id`** → garante **ordem por veículo** (o consolidador reconstrói viagem por `(vehicle_id, trip_id)` e precisa da ordem temporal de cada carro).
  - **partições = grau de paralelismo do consolidador** (começar com **3**; subir se o lag crescer).
  - **retenção curta** (ex.: **6h**) — o tópico é trânsito; o **durável é o landing no `raw`** (INFRA-02).
- **Redis:** confirmar acesso (127.0.0.1) + um `db`/namespace dedicado pro estado de viagem por veículo (e, no Bloco 3, o conjunto de carros ativos).
- Tudo via config do `uai-infra`; deploy por **GitHub Actions**; **nunca tocar o VPS direto**.

## Decisão
- **Tópico: `partitions=3`, `key=vehicle_id`, `retention=6h`** (recomendado; ajustável por lag/volume). Justificativa: ordem por veículo + paralelismo do consolidador + durabilidade fica no `raw`.

## Critério de pronto
- Tópico `ooh.rt.position` existe; o **poller publica** e o **consolidador consome**; ordem **por `vehicle_id`** preservada.
- Redis acessível pelo consolidador (namespace dedicado).

## Produz
- docs/epicos/runs/INFRA-01-kafka-topic-redis.md
