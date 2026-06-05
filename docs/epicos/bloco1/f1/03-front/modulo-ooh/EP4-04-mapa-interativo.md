# EP4-04 — Front: mapa interativo (reusa o mapa do legado) + toggles

> Bloco 1 (uAI-OOH F1) · **Épico EP4** (Front módulo OOH) · card 4 do kanban do EP4.
> **Depende de:** EP3 (shell/módulo + login SSO) · **EP2-08** (intel servir camadas GeoJSON do corredor) · EP2 tarefa 3 (ficha) · **Paralelizável:** parcial
> **Repo-alvo:** `uai-spark` (→ uai-portal — o módulo OOH mora no shell da plataforma; decisão 2026-06-05) · **Stack:** React / MapLibre (add maplibre-gl + porta o componente do legado `uai-buslines-web`)
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §5.2/§5.3.

## Objetivo
Renderizar o mapa da ficha **reaproveitando o componente MapLibre do legado** (`uai-buslines-web`, tag `legacy-frozen`) — recuperar e adaptar, **NÃO recriar do zero**. Camadas: **trajeto** (shape) · **corredor 300m** · **pontos** · **POIs por categoria**. **Toggles** de visibilidade por camada e por categoria de POI (como na simulação `docs/simulacao-linha-4107.html`).

## Como executar
1. **Recuperar o componente do legado:** `git show legacy-frozen:web/...` no `uai-buslines-web` — localizar o componente de mapa MapLibre + a lógica de toggles (chips `data-k` / `VIS[k]` de visibilidade).
2. **Adaptar pra consumir GeoJSON do intel** (não o backend legado): trajeto + pontos de `GET /api/lines/{id}`; **corredor 300m + POIs por categoria** de **EP2-08** (que lê o que o **EP1-02** materializa).
3. **Camadas MapLibre:** trajeto (line) · corredor (fill/line do buffer) · pontos (circle) · POIs (circle, cor por categoria: comércio/alimentação/saúde/educação).
4. **Toggles:** chips ligam/desligam camada e categoria (reusar o padrão de visibilidade do legado).
5. **Sem `ST_*`/PostGIS no front** — só renderiza GeoJSON pronto (ADR-003).

## Critério de pronto
- Mapa renderiza trajeto + corredor 300m + pontos + POIs consumindo **só GeoJSON do intel**.
- Toggles funcionam por camada **e** por categoria de POI.
- Componente **derivado do legado** (não reescrito do zero).

## Produz
- docs/epicos/runs/EP4-04-mapa.md
