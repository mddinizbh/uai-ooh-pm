# WEB-01 — mapa ao vivo (carros se movendo)

> F2 (Bloco 2) · lane **front-rt** · **Repo-alvo:** `uai-portal` *(estende o módulo OOH do F1)* · **Stack:** React / MapLibre
> **Depende de:** front F1 (EP4-04 mapa) + RT-01/RT-03 (intel-rt).

## Objetivo
Mostrar os **carros se movendo ao vivo** no mapa, **escopável por linha**, reusando o componente de mapa do F1.

## Como executar
- Estende o componente MapLibre do F1 (EP4-04) com uma **camada de veículos** (markers/símbolos com bearing) que atualiza a cada ~15s.
- Consome via hook **`usePositions(scope)`** (scope = linha) sobre a **`PositionFeed`** (polling no F2). O hook **encapsula o transporte** → trocar pra SSE/WS depois = mexer só no hook.
- Visual: posição + progresso (ex.: cor/ícone por completude da viagem); o conjunto vem **server-derived** (a tela passa a **linha**, não os `vehicle_id`).

## Decisão
- **Hook `usePositions(scope)` como costura swappable** (polling F2 → SSE/WS futuro = adapter interno).

## Critério de pronto
- Seleciona uma linha → **vê os carros dela se movendo** no mapa (atualiza ~15s); o hook isola o transporte (swap sem tocar a UI).

## Produz
- docs/epicos/runs/WEB-01-mapa-ao-vivo.md
