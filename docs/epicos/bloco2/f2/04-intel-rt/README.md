# 04 · intel-rt — 🟦 BACK

> Repo: **`uai-ooh-intel`** *(estende o intel do F1)* · leitura realtime + verificado.
> **Replanejado 2026-06-10** (face-centric — decisões F2-#3..#8 em [`../README.md`](../README.md)).
> **Depende de:** intel F1 (EP2 — scaffold/domínio/auth ✅ parcial) + Redis live (CONS-02/05) + `medido.viagem` + recompute da lane 06.

| Task | O que | Nota |
|---|---|---|
| [RT-01](RT-01-realtime-read.md) | **realtime read**: posição ao vivo + progresso **+ acumulado parcial** (lê as chaves `live:*` do Redis — contrato CONS-05) | conjunto por **LINHA**, sempre server-derived; primitivo tenant-agnóstico |
| [RT-02](RT-02-verificado-calibracao.md) | **verificado por linha**: `medido.viagem` (counts/km/completude/velocidade) + **`face_reach`/`line_reach` fonte MEDIDA** (serving, lane 06) lado a lado com o estimado · **selo por métrica (ADR-058)** | o **produto central** do F2 |
| [RT-03](RT-03-positionfeed-port.md) | **`PositionFeed` port** — polling ~15s no F2; SSE/WS = troca de adapter | conjunto **server-derived** (anti-vazamento) · sem PostGIS no read |

**Pronto:** mapa ao vivo (carros + contador subindo) de uma linha + números verificados comparáveis ao estimado, com selo honesto.
**Nota:** reusa a auth do F1 (EP2-07; introspection contra `uai-auth` já integrada no intel).
**Fronteira:** o intel **só lê** (Redis live + serving + `medido` read-only). Quem escreve é consolidador (fatos) e pipeline (modelo).
