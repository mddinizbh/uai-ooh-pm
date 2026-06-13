# WEB-01 — mapa ao vivo (carros se movendo + impressões subindo)

> F2 (Bloco 2) · lane **front-rt** · **Repo-alvo:** `uai-portal` *(módulo OOH)* · **Stack:** React / MapLibre
> **Depende de:** WEB-00 (mapa universal) + RT-01/RT-03 (intel-rt). · *(Replanejado 2026-06-10 — F2-#6)*

## Objetivo
Mostrar os **carros se movendo ao vivo** no mapa **+ os contadores subindo: impressões E alcance**
(acumulado parcial), escopável por linha — como **camadas do mapa universal (WEB-00)**.

## Como executar
- `VehiclesLayer` (markers com bearing, atualiza ~15s) + `AccumulatorOverlay` plugados no `UniversalMap`:
  **impressões** = soma de `impressoesParciais` dos carros do escopo; **alcance** = `alcanceParcial`
  (HLL com dedup, ~±0,8% — CONS-05). O alcance ao vivo **pode superar o estimado da linha** quando o
  carro roda fora da rota — mostrar como destaque positivo, não esconder (decisão do dono 2026-06-10).
- Consome via hook **`usePositions(scope)`** (scope = linha) sobre a **`PositionFeed`** (polling no
  F2). O hook **encapsula o transporte** → SSE/WS depois = mexer só no hook.
- Visual: posição + progresso (cor/ícone por completude); contador com **selo de estimativa**
  (ADR-058 — o número ao vivo nunca aparece como "medido").
- O conjunto vem **server-derived** (a tela passa a **linha**, nunca `vehicle_code`).

## Decisões
- **Hook `usePositions(scope)` como costura swappable** (polling F2 → SSE/WS futuro = adapter interno).
- **Acumulado é UX de produto** (o "impressões subindo" da demo) — mas rotulado estimativa, sempre.

## Critério de pronto
- Seleciona uma linha → vê os carros dela se movendo (~15s) **+ as impressões acumulando**; selo de
  estimativa visível; o hook isola o transporte (swap sem tocar a UI).

## Produz
- docs/epicos/runs/WEB-01-mapa-ao-vivo.md
