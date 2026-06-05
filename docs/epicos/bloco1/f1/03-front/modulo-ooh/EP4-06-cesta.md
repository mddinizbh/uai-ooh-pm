# EP4-06 — front: cesta + combinado

> Bloco 1 (uAI-OOH F1) · **Épico EP4** (front) · card 6 do kanban.
> **Depende de:** EP4-01 (`useAggregate` + `ProposalPort`) + EP2-05 (aggregate) · **Destrava:** EP4-07 (export) · **Paralelizável:** parcial
> **Repo-alvo:** `uai-spark` (→ uai-portal) · **Stack:** React 18 · TS · shadcn/ui · react-query
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §5.4.

## Objetivo
A **cesta** (seleção efêmera de linhas) + o **COMBINADO** via `/aggregate` + o gancho de **export**.

## Reusa do spark
- shadcn (`sheet`/drawer, `table`, `badge`, `button`) · `intelClient` (`useAggregate`).

## Cria
- **`CestaContext`** — estado client + **persist em `localStorage`** (client-only; sobrevive a refresh, **sem backend**).
- **＋/remover** linhas (da lista EP4-02 e da ficha EP4-03).
- **Cesta com 2 estados:**
  - **Fechada** = **barra/pill persistente sempre visível** com o **COMBINADO** (N linhas · impressões/dia · %AB) — o planejador vê o total correr enquanto adiciona linhas. **Largura = a do drawer** (alinhada à direita, não estica a tela inteira).
  - **Aberta** (drawer) = per-linha (com remover) + honestidade + exportar.
  - O COMBINADO (de `/aggregate`) recalcula a cada ＋/remover, visível nos dois estados.
- **Honestidade** do combinado (OTS, não alcance único, ±35%).
- Botão **exportar** → chama a `ProposalPort` (EP4-07).

## Decisão
- **Cesta = barra persistente (combinado sempre visível) + drawer (detalhe)** (decidido 2026-06-05): resolve o dilema drawer-vs-painel — a **barra fixa** mostra o COMBINADO o tempo todo; o **drawer** (shadcn `Sheet`) abre o per-linha + exportar sob demanda. Melhor dos dois.

## Honestidade
- Combinado = **OTS somado**, **não alcance único**; ±35% (vem do EP2-05). Sem "score combinado".

## Critério de pronto
- Adicionar/remover linhas (lista e ficha); cesta sobrevive a refresh (localStorage, client-only).
- **Barra persistente mostra o COMBINADO mesmo com o drawer fechado**, recalculando a cada ＋/remover; drawer (aberto) mostra per-linha + honestidade.
- Botão exportar chama a `ProposalPort`.

## Produz
- docs/epicos/runs/EP4-06-cesta.md
