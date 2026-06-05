# EP4-08 — front: honestidade na UI (cross-cutting)

> Bloco 1 (uAI-OOH F1) · **Épico EP4** (front) · card 8 do kanban — **cross-cutting** (toca lista/ficha/cesta/export). **Último card do EP4.**
> **Depende de:** EP4-02/03/06/07 (as superfícies) · **Paralelizável:** não (passa por cima das telas)
> **Repo-alvo:** `uai-spark` (→ uai-portal) · **Stack:** React 18 · TS · shadcn/ui
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §6 (honestidade); `proxies-e-premissas.md` (ledger).

## Objetivo
Materializar o diferencial **"honestidade de medição"** na UI: **separar o sólido do estimado** em toda tela, garantindo que nenhum número apareça mais preciso do que a fonte permite.

## Reusa do spark
- shadcn (`badge`, `tooltip`, `dialog`) · `proxies-e-premissas.md` (o ledger).

## Cria
- **`EstimativaBadge`** — componente reutilizável (`⚠️ ~est ±35% · OTS`).
- **Distinção visual sólido vs estimado** (cor/estilo): score/ranking = sólido; impressões = estimado.
- **Vigências** declaradas onde relevante (MCO set/2025 · censo 2022 · embarque mai/2024).
- **Explainer "metodologia / como ler"** (tooltip + modal).

## Onde aplica
- **Lista** (score sólido) · **Ficha** (badge + box nas impressões) · **Cesta** (combinado = OTS, não único) · **Export** (disclaimer no PDF).

## Decisão
- **Badges inline + modal de metodologia** (decidido/recomendado) — selo em todo número estimado + um modal "como ler" acessível (a honestidade é diferencial de venda; o planejador pode mostrar ao cliente). *(Alternativa: só badges inline.)*

## Critério de pronto
- Impressões **nunca** aparecem sem o selo de estimativa; score/ranking marcados como sólido.
- **Consistente nas 4 superfícies** (lista/ficha/cesta/export).
- Modal de metodologia acessível; vigências declaradas.

## Produz
- docs/epicos/runs/EP4-08-honestidade.md
