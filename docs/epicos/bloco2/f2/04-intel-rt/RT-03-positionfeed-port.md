# RT-03 — `PositionFeed` port (polling, swappable pra SSE/WS)

> F2 (Bloco 2) · lane **intel-rt** · **Repo-alvo:** `uai-ooh-intel` *(estende F1)* · **Stack:** Java/Spring
> **Depende de:** RT-01. · *(2026-06-10: payload ganha o acumulado parcial — F2-#6; sem mudança estrutural)*

## Objetivo
Desenhar o **transporte** do realtime como uma **porta** (`PositionFeed`), de modo que **polling hoje** e **SSE/WS amanhã** sejam **troca de adapter**, sem mexer no front nem no domínio.

## Como executar
- **F2 = polling:** o endpoint do RT-01 (`GET /positions?line=`) é **stateless** e **cacheável curto** (~ o cadence de 15s) — o front faz GET a cada ~15s. **Sem conexão always-on.**
- **`PositionFeed` (contrato):** "dado um **escopo** (linha no F2; campanha+tenant no Bloco 3 via `CampaignScope`), devolva posições **+ acumulado parcial** do conjunto **server-derived**". O adapter F2 = query no Redis (chaves `live:*`, contrato CONS-05); um futuro adapter SSE/WS implementa o **mesmo contrato** com push.
- `RealtimeScope` **sealed** (variante carrega dado): F2 = `LineScope`; Bloco 3 adiciona `CampaignScope`. Sem `default`.
- O front consome via hook `usePositions(scope)` (WEB-01) — o swap poll↔SSE↔WS é **interno ao adapter**.

## Decisão (F2-#2, decidida 2026-06-06)
- **Polling no F2** (cadence 15s, stateless, sem always-on) atrás da `PositionFeed`. **WS a um adapter de distância.** Regra **anti-vazamento** (conjunto server-derived) embutida no contrato.

## Critério de pronto
- `GET /positions?line=` responde rápido (Redis), **stateless**, escopável; o contrato `PositionFeed` desenhado pra adapter SSE/WS futuro **sem tocar** front nem domínio; teste de contrato (controller depende só da porta).

## Produz
- docs/epicos/runs/RT-03-positionfeed-port.md
