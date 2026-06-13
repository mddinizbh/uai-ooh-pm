# RT-01 — realtime read (posição + progresso + acumulado ao vivo)

> F2 (Bloco 2) · lane **intel-rt** · **Repo-alvo:** `uai-ooh-intel` *(estende F1)* · **Stack:** Java/Spring
> **Depende de:** intel F1 (EP2) + Redis live (contrato **CONS-05**). · *(Replanejado 2026-06-10 — inclui o acumulado, F2-#6)*

## Objetivo
Servir **posição ao vivo + progresso + acumulado parcial** ("impressões subindo") para um conjunto de
`vehicle_code` — lendo as chaves **`live:*`** do Redis (escritas pelo consolidador). No F2 o conjunto
vem de um **filtro por LINHA**.

## Como executar
- `GET /api/realtime/positions?line={lineId}` → resolve o conjunto **no servidor** via índice
  `live:line:{lineId}` (SET mantido pelo CONS-05) e devolve por carro: última posição
  (lat/lon/bearing), `current_stop_sequence`, completude parcial, **`impressoesParciais` +
  `alcanceParcial` (HLL, dedup ~±0,8%) + `hexesVisitados`** (o acumulado ao vivo), timestamp.
  O alcance ao vivo pode **superar o estimado da linha** quando o carro roda fora da rota —
  é feature, não bug (decisão do dono 2026-06-10; ver CONS-05 §Motivo).
- Lê **só do Redis** (não toca `medido`/`core` no hot path). Sem PostGIS (ADR-003).
- **Anti-vazamento (F2-#2):** conjunto **sempre server-derived**; o cliente nunca passa `vehicle_code`.
- **Honestidade:** o payload do acumulado carrega o selo **estimativa** (ADR-058) — o front rotula.

## Decisões
- **Conjunto = filtro por LINHA** no F2 (no Bloco 3 o escopo vira campanha+tenant via `CampaignScope`, mesmo primitivo).
- O intel **não chama o consolidador** (headless): o contrato é o Redis (CONS-05).

## Critério de pronto
- `GET positions?line=4107` → posições + progresso + acumulado **dos carros da 4107 ao vivo**; conjunto derivado no servidor; sem PostGIS; IT com Redis seedado no contrato CONS-05.

## Produz
- docs/epicos/runs/RT-01-realtime-read.md
