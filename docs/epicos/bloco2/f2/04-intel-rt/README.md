# 04 · intel-rt — 🟦 BACK

> Repo: **`uai-ooh-intel`** *(estende o intel do F1)* · leitura realtime + verificado.
> **Depende de:** intel F1 (EP2 — scaffold/domínio/auth) + Redis (estado do CONS) + `core.trip_executed`.

| Task | O que | Nota |
|---|---|---|
| RT-01 | **realtime read**: posição ao vivo + progresso de viagem p/ um **conjunto de `vehicle_id`** (lê do Redis) | conjunto vem de **filtro por LINHA** (eval); primitivo tenant-agnóstico |
| RT-02 | **verificado por linha**: `core.trip_executed` + `pattern_stop_exposure.v_real` → métricas verificadas **+ loop de calibração** (verificado vs estimado do F1) | o **produto central** do F2 |
| RT-03 | **`PositionFeed` port** — transporte do mapa ao vivo. **F2 = polling** (~15s, stateless, sem conexão always-on); SSE/WS = **troca de adapter** | **conjunto de `vehicle_id` SEMPRE server-derived** do escopo autenticado (anti-vazamento) · sem PostGIS no read |

**Pronto:** mapa ao vivo dos carros de uma linha + números verificados comparáveis ao estimado.
**Nota:** reusa a auth do F1 (EP2-07, modo stub enquanto `uai-auth` não sobe).
