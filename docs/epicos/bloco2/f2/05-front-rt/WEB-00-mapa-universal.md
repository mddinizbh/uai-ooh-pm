# WEB-00 — componente de mapa universal (camadas plugáveis)

> F2 (Bloco 2) · lane **front-rt** · **Repo-alvo:** `uai-portal` *(módulo OOH)* · **Stack:** React / MapLibre / TS
> **Novo no replanejamento 2026-06-10.** Base de WEB-01/02 — e do mapa do F1 (EP4-04), que converge pra cá.

## Objetivo
Um **único componente de mapa** do vertical OOH com **camadas plugáveis**, em vez de um mapa por tela:
superfície H3 × hora (hexágonos coloridos por métrica), shapes/corredores de linha, veículos ao vivo,
acumulado. Toda tela de mapa (F1 ficha, F2 ao vivo, B3 campanha) compõe as camadas que precisa.

## Referência de UX e contrato (a base pedida)
**`uai-ooh-pipeline/docs/design/mapa-alcance-simulacao.html`** — simulação "superfície × hora × 2 faces":
hexágonos H3 coloridos por métrica, **slider de hora + play/animação**, troca de face/métrica, tooltip
por hexágono. Os dados embutidos (`const HEXES`, `const METRICS`) **documentam o contrato de dados**
que o componente consome (hex → valor por faixa horária / face).

⚠️ É **referência de UX e de contrato, não código a reusar**: o HTML é Leaflet bundlado/minificado;
o portal implementa em **MapLibre** (stack do F1/EP4-04) com TS estrito.

## Como executar
- `modules/ooh/map/UniversalMap.tsx` + camadas: `H3SurfaceLayer` (hex × hora × métrica, com slider/play),
  `ShapeLayer` (corredor/linha), `VehiclesLayer` (do WEB-01), `AccumulatorOverlay` (contador).
- Props orientadas a **camadas declarativas** (`layers={[...]}`) — telas compõem sem tocar no mapa.
- Dados da superfície vêm do intel (serving `exposure_cell`/`face_reach` — endpoints do F1/EP2);
  o contrato espelha o `HEXES`/`METRICS` da simulação.
- Migrar o mapa existente do F1 (EP4-04 `CorridorMap`) pra dentro do universal quando o F2 front entrar
  (sem big-bang: WEB-01 nasce no universal; o F1 converge depois).

## Decisão
- **MapLibre, não Leaflet** (consistência com EP4-04). A simulação é o **espelho de UX** a manter.

## Critério de pronto
- Componente renderiza superfície H3 × hora com slider/play (paridade visual com a simulação),
  + camada de shape; WEB-01 pluga veículos sem alterar o componente base.

## Produz
- docs/epicos/runs/WEB-00-mapa-universal.md
