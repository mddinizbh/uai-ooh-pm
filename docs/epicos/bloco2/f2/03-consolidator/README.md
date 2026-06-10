# 03 · consolidator — 🟦 BACK

> Repo: **`uai-ooh-trip-consolidator`** *(novo, Java 21/Spring + Spring Kafka + Redis + h3-java)* · reconstrói
> viagens do RT, produz a **cobertura H3 medida** e o **acumulado ao vivo**. **Worker headless** (sem HTTP além
> de health) — **não se funde com o intel** (F2-#4).
> **Replanejado 2026-06-10** (face-centric): escreve no **schema `medido`** (fatos, Flyway próprio — F2-#3),
> fato **puro** sem `campaign_id` (F2-#5). Ver [`techspec.md`](techspec.md) + decisões em [`../README.md`](../README.md).
> **Depende de:** INFRA ✅ + POLL ✅ (streaming desde 09/jun) + `core` (`trip_pattern`/`line_shape`/`h3_cell` — ✅).
> **Pode começar JÁ.**

| Task | O que | Nota |
|---|---|---|
| [CONS-01](CONS-01-scaffold.md) | scaffold (template) + config + **Flyway do schema `medido`** | dono do DDL = quem grava |
| [CONS-02](CONS-02-consumo-estado.md) | consome `ooh.rt.position`; estado por veículo no Redis + **h3 local por posição** (lib H3, sem banco); frota inteira | idempotência por `feed_timestamp` |
| [CONS-03](CONS-03-reconstrucao-viagem.md) | **reconstrução de viagem** (5 condições + timeout 10min); linha **do RT** (96,7%; S* → `padrao_desconhecido`) | identidade: `vehicle_code` = `vehicle.id` do feed (F2-#8) |
| [CONS-04](CONS-04-fechamento-medido.md) | fechamento: snap PostGIS (31983) → km/completude/`v_real` + **cobertura H3 EXATA** (interpolada pelo shape) → **`medido.*`** + **`ooh.trip.completed` enriquecido** | sem campaign_id; ON CONFLICT seguro |
| [CONS-05](CONS-05-acumulador-ao-vivo.md) | **acumulador ao vivo**: hexes com ping + `impressoesParciais` no Redis (`live:vehicle:{}`/`live:line:{}`) — o contrato que o RT-01 lê | aproximado ao vivo, exato no fechamento; selo estimativa (ADR-058) |

**Pronto:** viagens da 4107 fecham (nº/dia ~ frequência, km ~ extensão×viagens), `medido.viagem_hex`
contíguo ao corredor, acumulado ao vivo reconcilia com o fechamento.
**Deploy/automação:** o serviço no compose da VPS (GHCR + envs + restart + healthcheck) é entrega da
**INFRA-04** — fecha junto com o CONS-04.
**Fixture de teste (carros de referência):** `11198 · 20736 · 30835 · 40705 · 40806` — 1+ por
consórcio; detalhes e o que cada um valida na [techspec](techspec.md) §Fixture.
**Saída consumida por:** lane 06 (recompute `face_reach` fonte medida) + RT-01/RT-02 + módulo OOH do CMS (B3, via evento).
