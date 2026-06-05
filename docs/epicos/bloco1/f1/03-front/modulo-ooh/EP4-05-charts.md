# EP4-05 — front: charts interativos

> Bloco 1 (uAI-OOH F1) · **Épico EP4** (front) · card 5 do kanban · **★ alto reuso** (recharts já no spark).
> **Depende de:** EP4-03 (slots da ficha) + EP4-01 (intelClient) + EP2-03 (metrics) · **Paralelizável:** parcial
> **Repo-alvo:** `uai-spark` (→ uai-portal) · **Stack:** React 18 · TS · **recharts** · shadcn
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §5.3.

## Objetivo
Os **charts interativos** da ficha, plugando nos slots do EP4-03. Reusa `recharts` + `ui/chart.tsx` (já no spark).

## Charts
- **Demanda** — `BarChart` (pax útil/sáb/dom). ← `/metrics`
- **Perfil classe A–E** — `BarChart` (distribuição). ← `line_profile_demografico` (via `/metrics`)
- **5 sub-scores** (`ScoreBreakdown`) — **`RadarChart`** (a "forma" da linha). ← `/metrics`
- **Interativos:** tooltip no hover (recharts default) — requisito do produto.

## Reusa do spark
- **recharts** + `ui/chart.tsx` · shadcn (`card`) · `intelClient` (`useLineMetrics`).

## Decisão
- **5 sub-scores como `RadarChart`** (decidido/recomendado) — mostra a "forma" da linha nos 5 eixos de uma vez (lê força/fraqueza, compara perfis). *(Alternativa: 5 barras — valor exato mais fácil, menos identidade visual.)*

## Critério de pronto
- 3 charts renderizam com dados reais do `/metrics`, **interativos** (tooltip no hover).
- Plugam nos slots da ficha (EP4-03).

## Produz
- docs/epicos/runs/EP4-05-charts.md
