# CONS-05 — acumulador ao vivo (contrato Redis lido pelo intel)

> F2 (Bloco 2) · lane **consolidator** · **Repo-alvo:** `uai-ooh-trip-consolidator` · **Stack:** Java + Redis
> **Depende de:** CONS-02. **Novo no replanejamento 2026-06-10 (F2-#6 — o "impressões subindo ao vivo" entrou no F2).**

## Objetivo
Manter, **por posição recebida**, o **acumulado parcial da viagem aberta** (hexes visitados + impressões parciais) e o **índice por linha** — o contrato Redis que o **RT-01 lê** pro mapa ao vivo e pro contador subindo. O consolidador é a **única escrita**; o intel só lê.

## Contrato (chaves Redis)
```
live:vehicle:{vehicleCode}   HASH   lat, lon, bearing, lineId, tripId, currentStopSequence,
                                    completudeParcial, impressoesParciais, hexesVisitados, ts
                                    TTL 5min (carro some do mapa se o feed parar)
live:line:{lineId}           SET    vehicleCodes rodando a linha AGORA — índice pro conjunto
                                    server-derived do RT-01 (anti-vazamento, F2-#2)
```

## Acumulado (aproximado ao vivo, exato no fechamento)
- Quando a posição entra num hex **novo** da viagem: `impressoesParciais += exposure(hex, tipoDia, faixa) × coef`.
- `core.exposure_cell` (188k linhas) **cacheada em memória no boot** — zero banco no hot path.
- **Mesma régua da lane 06** (fonte única da fórmula face-centric) → a soma incremental **reconcilia** com o agregado do fechamento (|Δ| pequeno; o ao vivo só vê hexes com **ping**, o fechamento completa com interpolado).
- Fechou a viagem (CONS-04): acumulado **zera**; o exato já está no `medido`.

## Honestidade (ADR-058)
O número ao vivo é **estimativa** (coeficientes de visada cegos) — o contrato carrega isso e o
front rotula (WEB-01). Nunca apresentar como "medido".

## Critério de pronto
- IT: sequência de posições → hashes/sets corretos, TTL renovado, índice por linha consistente.
- Reconciliação na 4107: |Σ incremental − agregado do fechamento| pequena e explicável (ping vs interpolado).
- Intel lê posição+acumulado **sem nenhuma chamada ao consolidador**.

## Produz
- docs/epicos/runs/CONS-05-acumulador-ao-vivo.md
