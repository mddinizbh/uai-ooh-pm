# EP4-09 — front: mapa da cesta (alcance combinado)

> Bloco 1 (uAI-OOH F1) · **Épico EP4** (front) · card 9 do kanban — **NOVO** (feature 2026-06-05).
> **Depende de:** EP4-06 (cesta) + EP4-04 (mapa) + EP2-08 (`/geo`) · **Alimenta:** EP4-07 (export com snapshot) · **Paralelizável:** parcial
> **Repo-alvo:** `uai-spark` (→ uai-portal) · **Stack:** React 18 · TS · MapLibre
> **Origem:** pedido do dono 2026-06-05 — "ver o alcance da cesta".

## Objetivo
No **drawer da cesta**, um botão **🗺️ "ver mapa da cesta"** abre um mapa com **todas as linhas da cesta plotadas** (cores distintas), pro usuário ver a **cobertura/alcance combinado**. O **snapshot** desse mapa vai pro **PDF de proposta** (EP4-07).

## Reusa
- O componente de mapa do **EP4-04** (MapLibre) · geo por linha `/lines/{id}/geo` (EP2-08) — busca as N da cesta · shadcn (`sheet`/`dialog`) p/ abrir.

## Cria
- View **"mapa da cesta"**: plota as N linhas (cores distintas) + legenda.
- **Toggle dos corredores 300m** (on/off) no mapa — reusa o padrão de toggle do EP4-04. Trajetos sempre visíveis; o overlay de corredor liga/desliga.
- **Snapshot** do mapa (canvas → PNG) p/ o export — `map.getCanvas().toDataURL('image/png')` (requer o mapa com **`preserveDrawingBuffer: true`**). O snapshot reflete o estado atual do toggle.
- Botão no drawer + estado (abre/fecha).

## Decisão (resolvida 2026-06-05)
- **Trajetos sempre + corredores 300m com TOGGLE, padrão OFF** — o mapa abre **só com os trajetos** (limpo); o usuário **liga o toggle** pra ver o overlay de corredor (a faixa de cobertura/alcance). Evita poluição visual quando os corredores se sobrepõem. Reusa `/geo` (já traz o corredor) e o padrão de toggle do EP4-04.

## Honestidade
- A cobertura mostra corredores que **se sobrepõem** (OTS), **não alcance único** — coerente com o combinado (EP2-05).

## Critério de pronto
- Botão no drawer abre o mapa com **todas** as linhas da cesta plotadas (cores + legenda).
- **Snapshot (PNG)** gerado e **incluído no export PDF** (EP4-07).
- Marcação de honestidade (cobertura ≠ alcance único).

## Produz
- docs/epicos/runs/EP4-09-mapa-cesta.md
