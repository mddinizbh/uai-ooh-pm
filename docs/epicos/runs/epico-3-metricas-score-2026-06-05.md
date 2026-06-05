# Run — Épico 3 (Métricas + Score): T11–T16 · 2026-06-05

> Execução no `uai-ooh-pipeline` (branch `feat/ooh-epico3-metricas-score`), consumindo a **recalibração
> pós-Épico 2** deste hub. Estado **verificado no banco `ooh`** (schema `core`) via MCP `postgres-ooh`
> em 2026-06-05. Run detalhado de implementação: vault → `logs/2026-06-04-ooh-epico3-metricas-score.md`.

## Estado materializado no `core` (counts reais)

| Task | Tabela `core` | Linhas | Veredito |
|---|---|---|---|
| T11 (3.1) | `line_profile_demografico` | 1.429 (linha×classe) | ✅ |
| T11–T16 | `line_metrics` | **303** (1 linha sem shapes ficou fora) | ✅ |
| T16 (3.6) | `score_total` + 5 sub-scores | **0 nulls** | ✅ |
| — | `dataset_version` | **1 / BUILDING** (reusada; não promovida) | ✅ |

## Score 0–100 — distribuição e ranking (sanity)

- 303 linhas, **0 nulls** em `score_total` e nos 5 sub-scores; faixa **3,2–75,7**, média **39,1**.

| Pos | Linha | Score | %AB | POI/km | Arterial |
|---|---|---:|---:|---:|---:|
| 1/303 | SC02A — Praça 7/Savassi | 75,7 | 96,7 | 1660 | 94% |
| 2/303 | 62 — Venda Nova/Savassi | 72,3 | 65,1 | 361 | 88% |
| 3/303 | 8106 — Santa Cruz/BH Shopping | 71,2 | 78,7 | 541 | 69% |
| **7/303** | **4107 — Alto Caiçara/Serra** | **67,2** | 78,6 | 781 | 48% |
| 301/303 | 902 — Taquaril/Castanheiras | 5,7 | 0,0 | 21 | 4% |
| 302/303 | 321 — Olhos D'Água/Pilar | 3,5 | 0,0 | 28 | 3% |
| 303/303 | 336 — Vila Bernadete | 3,2 | 1,1 | 11 | 0% |

Eixos centrais/nobres no topo, periferia no fundo, **4107 em #7/303** (patamar coerente previsto no épico-3).

## Recalibração (pós-Épico 2) — confirmada nas colunas do `core.line_metrics`

- `pct_ab`/`pct_de` = **quintil-de-BH** (relativa); `s_perfil` sobre `pop_corredor_pond`.
- `densidade_poi_km` (Overture rel≥1) entra no `s_poi`; `n_poi_total_prospeccao` (bruto multi-fonte)
  guardado **separado** (prospecção, não score).
- Impressões F1 = fórmula simples (`e_pop_util`/`e_pax_util`/`impressoes_*`); `faixa_indicativa_pct`
  carimbada. Velocidade de passagem / `E_traf` = v2.

## Pendência

- **T17** (serving) NÃO rodou: `serving.*` **vazio**; `dataset_version` segue **BUILDING**. Próximo passo.
