# 03 · consolidator — 🟦 BACK

> Repo: **`uai-ooh-trip-consolidator`** *(novo, Java/Spring + Spring Kafka + Redis)* · reconstrói viagens do RT.
> **Depende de:** INFRA (Kafka/Redis) + POLL (tópico) + `core` (`trip_pattern`/`line_shape` — ✅ F1). **Worker contínuo.**

| Task | O que | Nota |
|---|---|---|
| CONS-01 | scaffold (Java/Spring + Spring Kafka + Redis) do `uai-ooh-service-template` | regenerar do template |
| CONS-02 | consome `ooh.rt.position`; **estado por veículo em Redis**; **modo frota inteira** | F2 **sem** filtro de ativos (comercial = Bloco 3) |
| CONS-03 | **reconstrução de viagem**: sequência contígua `(vehicle_id, trip_id)`/dia; fecha por mudança `trip_id` / reset `current_stop_sequence` / timeout / último ponto do padrão. **Linha-do-RT**: `trip_id→route_id→core.line` (96,7%; 3,3% supl → `padrao_desconhecido` por route+dir+geo) | **nunca MCO** |
| CONS-04 | **km + completude + velocidade/ponto** (snap no shape, PostGIS no `core`) → `core.trip_executed` + `core.trip_executed_track` (downsampled ~1/30s) + `pattern_stop_exposure.v_real`; emite `ooh.trip.completed` | `campaign_id` **NULL** no F2 (token de correlação fica pro Bloco 3, ADR-052) |

**Pronto:** viagens da 4107 fecham com nº/dia ~ frequência e km ~ extensão×viagens.
**Nota F2:** sem comercial, a **flag de divergência-de-linha fica latente** (não há "linha esperada da campanha") — reativa no Bloco 3.
