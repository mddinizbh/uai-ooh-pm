# EP4-03 — front: ficha (master-detail)

> Bloco 1 (uAI-OOH F1) · **Épico EP4** (front) · card 3 do kanban.
> **Depende de:** EP4-01 (intelClient) + EP2-03 (ficha/metrics) · **Slots:** EP4-04 (mapa) · EP4-05 (charts) · **Paralelizável:** parcial (com EP4-02)
> **Repo-alvo:** `uai-spark` (→ uai-portal) · **Stack:** React 18 · TS · shadcn/ui
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §5.2; `docs/simulacao-linha-4107.html` (layout de referência).

## Objetivo
**Container da direita** do master-detail: ao selecionar uma linha, busca `/lines/{id}` + `/metrics` e renderiza a ficha no layout do 4107. Tem **slots** onde o **mapa (EP4-04)** e os **charts (EP4-05)** plugam.

## Layout (resolvido 2026-06-05)
- **Master-detail confirmado, com lista SLIM:** a lista (EP4-02) vira sidebar fininha (~260px) e a **ficha ocupa ~75%** — assim a ficha rica respira (mapa no topo + seções 2-col). Resolve a dúvida de "ficha apertada no painel" (não era o master-detail, era a lista larga demais). Troca de linha é instantânea, sem navegação.

## Reusa do spark
- shadcn (`card`, `separator`, `badge`, `skeleton`) · layout do `simulacao-linha-4107.html` (referência) · `intelClient` (`useLineDetail` + `useLineMetrics`).

## Cria
- Container da ficha + **header** (linha + badges score/classe/AB + **＋ cesta**).
- Seções: **alcance** · **perfil renda/classe** · **arterial & POIs** · **impressões + box de honestidade**.
- **Slots** p/ mapa (EP4-04) e charts (EP4-05).
- **Empty state** ("selecione uma linha") + **skeleton** no loading.

## Decisão
- **Carregamento: `/metrics` na hora, `/geo` lazy** (decidido/recomendado) — a ficha abre rápida com os números; o mapa+POIs (`/geo`) carrega sob demanda (viewport/aba), porque os POIs do corredor podem ser pesados. *(Alternativa: carregar tudo de uma vez.)*

## Critério de pronto
- Selecionar uma linha carrega a ficha com header + todas as seções (números reais do `/metrics`).
- Slots de mapa/charts presentes (preenchidos por EP4-04/05).
- Empty state + skeleton; box de honestidade nas impressões.

## Produz
- docs/epicos/runs/EP4-03-ficha.md
