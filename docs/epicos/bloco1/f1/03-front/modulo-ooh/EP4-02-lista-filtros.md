# EP4-02 — front: lista + filtros (ranking)

> Bloco 1 (uAI-OOH F1) · **Épico EP4** (front) · card 2 do kanban.
> **Depende de:** EP4-01 (intelClient) + EP2-04 (ranking) + EP2-06 (filtros) · **Gate:** região depende do EP1 · **Paralelizável:** parcial (com EP4-03)
> **Repo-alvo:** `uai-spark` (→ uai-portal) · **Stack:** React 18 · TS · shadcn/ui · react-query
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §5.2.

## Objetivo
A **esquerda do master-detail**: lista enxuta ordenável + filtros. Consome `/ranking` + `/regions`. A **seleção dirige a ficha** (EP4-03).

## Reusa do spark
- shadcn (`select`, `slider`, `toggle`, `scroll-area`) · `intelClient` (EP4-01) · react-query.

## Cria
- **Lista enxuta** (nº · linha · score), ordenável + **botão de ação ＋ cesta por linha** — quick-add **direto da lista**, sem precisar abrir a ficha (usabilidade). Estado do botão reflete se a linha já está na cesta (＋ / ✓ na cesta).
- **Filtros:** público-alvo AB/DE · **região→bairro** (cascata de `/regions`) · **pesos** (5 sliders + reset ao default).
- **Estado de seleção** → dirige a ficha (EP4-03).

## Fonte da lista
- Sempre `GET /api/lines/ranking` com os params atuais (pesos + público + região/bairro). Sem params = ordem do score base. **Um endpoint só** pra lista.

## Decisão
- **UI dos pesos: 5 sliders + reset** (decidido/recomendado p/ F1) — controle fino dos sub-scores + voltar ao default. *(Presets "Alcance-first/Perfil-first" ficam pós-F1.)*

## Critério de pronto
- Lista mostra as **303** ordenadas por score; reordena com pesos/público/região via `/ranking`.
- Cascata região→bairro funciona (de `/regions`).
- Clicar numa linha **seleciona** (dirige a ficha); **＋** adiciona à cesta.

## Produz
- docs/epicos/runs/EP4-02-lista-filtros.md
