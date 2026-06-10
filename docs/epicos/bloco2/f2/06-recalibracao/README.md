# 06 · recalibracao — 🟨 DATA

> Repo: **`uai-ooh-pipeline`** *(estende o normalizer)* · injeta a trajetória **MEDIDA** na máquina face-centric.
> **Lane criada no replanejamento 2026-06-10** (F2-#7 — recompute é do pipeline, ADR-050: "consolidador grava fatos; pipeline recomputa modelo").
> **Depende de:** CONS-04 rodando alguns dias (volume mínimo em `medido.viagem_hex`) + Onda 2 ✅ (`exposure_cell`/`face_reach`/`od_trip`).

| Task | O que | Nota |
|---|---|---|
| [RECAL-01](RECAL-01-recompute-fonte-medida.md) | **recompute** de `face_reach`/`line_reach` com fonte **`medido.viagem_hex`** (mesma fórmula da Onda 2, só troca a trajetória) + agregação do `v_real` no modelo · batch **D-1** | flag `--fonte=medida` no CLI `normalizer reach` |
| [RECAL-02](RECAL-02-validacao-reconciliacao.md) | **validação/reconciliação** na 4107: medido vs estimado, incremental vs agregado, selos ADR-058 | counts via MCP `postgres-ooh` |

**Pronto:** `face_reach`/`line_reach` fonte medida no `core`/`serving`, comparáveis ao estimado —
o **produto central do F2** (o RT-02 expõe; o WEB-02 mostra).
**Honestidade:** o recompute torna reais trajetória/frequência/velocidade; coeficientes de **visada
seguem cegos** (Fase C) — selo de estimativa mantido no reach (ADR-058).
