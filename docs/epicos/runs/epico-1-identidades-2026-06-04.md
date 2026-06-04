# Run retroativo — Épico 1 (Identidades): T1–T7 · 2026-06-04

> ⚠️ **Apontamento RETROATIVO.** As tasks T1–T7 foram executadas no `uai-ooh-pipeline` antes da
> convenção de tracking existir, sem gerar `run` no momento. Este apontamento **reconstrói o estado a
> partir do banco `ooh`** (schema `core`), verificado em **2026-06-04** via MCP `postgres-ooh`. Não é um
> log de execução observada; é uma validação de estado. Daqui pra frente, **cada task gera seu próprio
> `run` no momento da execução** (T8+).

## Origem da evidência
- Commits no `uai-ooh-pipeline`: `0d75ded` (scaffolding T0a), `00126d7` (scripts movidos), `fc32823`/`794da5a`
  (ingestor POI), **`01c69d0`** ("normalização raw→core do Épico 1 — Identidades, chunked + resume").
- Módulos: `ooh_pipeline/normalizer/identidades/` (dataset_version, line, trip_pattern, stop, line_shape,
  line_stop, vehicle, run, validate).

## Estado materializado no `core` (counts reais)

| Task | Tabela(s) `core` | Linhas | Alvo do épico | Veredito |
|---|---|---|---|---|
| T2 (1.0) | `dataset_version` | 1 (ativa) | 1 versão de build | ✅ |
| T3 (1.1) | `line` | **304** | 304 linhas | ✅ bate |
| T3 (fundação) | `trip_pattern` · `pattern_stop` | 2.673 · 149.390 | modelo canônico (pattern-centric) | ✅ existe |
| T4 (1.2) | `stop` | **9.650** | ~9,6k stops c/ `siu`/geo | ✅ bate |
| T5 (1.3) | `line_shape` | 1.525 | shape por serviço/sentido | ✅ |
| T6 (1.4) | `line_stop` | 84.992 | sequência ponto×linha×sentido×dia | ✅ |
| T7 (1.5) | `vehicle` · `vehicle_line_history` | 2.653 · 14.815 | frota (auxiliar) | ✅ |
| — | `build_progress` (controle resume/chunk) | 13.818 | — | infra de pipeline |

## Leitura
- **Épico 1 = COMPLETO e materializado.** A fundação canônica `trip_pattern` (que era a pendência
  bloqueante §1.6 do plano) está resolvida — `line_shape`/`line_stop` derivam dela.
- Os números-âncora batem com os alvos (304 linhas, ~9,6k stops).

## Pendência de validação fina (quando o MCP voltar)
- Confirmar a **4107** contra o `core` (renda do corredor, % stops c/ `siu`, comprimento do shape) — não
  reexecutado neste apontamento (validação de invariantes, não só de counts).
- Conferir distribuição de `match_dist_m` em `stop` (mediana ~0, p90 <5m) — alvo da T4.

## Estado do `dataset_version`
- 1 versão ativa para o build F1. Vira `ACTIVE` definitivamente ao fim do Épico 3 (T16/T17).
