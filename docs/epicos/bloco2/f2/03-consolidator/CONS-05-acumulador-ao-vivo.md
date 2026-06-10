# CONS-05 — acumulador ao vivo: impressões + ALCANCE com dedup (contrato Redis lido pelo intel)

> F2 (Bloco 2) · lane **consolidator** · **Repo-alvo:** `uai-ooh-trip-consolidator` · **Stack:** Java + Redis (HLL nativo)
> **Depende de:** CONS-02 + **RECAL-00** (HLLs de audiência por hex no Redis — pipeline). **Novo no replanejamento 2026-06-10 (F2-#6); alcance ao vivo adicionado na mesma data (decisão do dono — ver Motivo).**

## Objetivo
Manter, **por posição recebida**, o acumulado ao vivo da viagem/dia — **impressões parciais** (soma)
**e alcance parcial com dedup** (HyperLogLog) — e o índice por linha. É o contrato Redis que o
**RT-01 lê**. O consolidador é a **única escrita**; o intel só lê.

## Motivo do alcance ao vivo (decisão do dono, 2026-06-10)
O carro pode rodar **fora da rota da linha** e alcançar **mais gente que o alcance fixo estimado
pelo corredor** — mostrar isso subindo ao vivo é argumento de produto ("entregou mais que o
estimado"). Soma de hexes mentiria (pessoas repetem entre hexes); **HLL deduplica** com erro ~0,8%.

## Contrato (chaves Redis)
```
live:vehicle:{vehicleCode}    HASH  lat, lon, bearing, lineId, tripId, currentStopSequence,
                                    completudeParcial, impressoesParciais, hexesVisitados,
                                    alcanceParcial (cache do último PFCOUNT), ts · TTL 5min
live:line:{lineId}            SET   vehicleCodes na linha agora (conjunto server-derived do RT-01)
live:reach:trip:{vehicleCode} HLL   alcance da viagem aberta (PFMERGE dos hexes novos) · zera no fechamento
live:reach:day:{vehicleCode}  HLL   alcance acumulado do service_date · expira na virada do dia
hll:hex:{h3}:{tipoDia}:{faixa} HLL  audiência OD do hex — PRÉ-COMPUTADO pelo pipeline (RECAL-00, read-only aqui)
```

## Acumulação (por posição, quando entra em hex NOVO da viagem)
- **Impressões:** `impressoesParciais += exposure(hex, tipoDia, faixa) × coef` (cache de
  `core.exposure_cell` em memória — mesma régua da lane 06, reconcilia com o agregado).
- **Alcance:** `PFMERGE live:reach:trip:{v} hll:hex:{...}` (+ idem no `day`) → `PFCOUNT` =
  alcance com **dedup real** (~±0,8%); cacheado no hash pro RT-01 não pagar PFCOUNT por request.
- Fechou a viagem (CONS-04): `trip` zera (o exato vai pro `medido`/RECAL); `day` segue até virar o dia.

## Honestidade (ADR-058)
Impressões ao vivo = estimativa (coeficientes cegos). Alcance ao vivo = **aproximado (HLL ±0,8%) e
parcial (só hexes com ping)** — selo estimativa; o exato (SQL, D-1, com interpolação) substitui no
consolidado. O front rotula (WEB-01); nunca apresentar como "medido".

## Riscos / decisões locais
- **Memória dos HLLs por hex** (188k chaves, sparse na maioria — audiência média ~136/hex×faixa):
  estimar na RECAL-00; se passar do orçamento do Redis da VPS, carregar só os hexes de BH com
  audiência > 0 e/ou HLLs em Postgres com load lazy. Medir antes de otimizar.
- Veículo sem hex novo no ciclo → zero trabalho de HLL (merge só na entrada de hex novo).

## Critério de pronto
- IT: sequência de posições → hashes/sets/HLLs corretos; TTLs; índice por linha consistente.
- Dedup comprovado: posições repetidas no mesmo hex não inflam `alcanceParcial`.
- Reconciliação na 4107: |PFCOUNT ao vivo − alcance exato do fechamento| ≤ ~5% (HLL + ping vs interpolado), explicável.
- Intel lê posição+impressões+alcance **sem nenhuma chamada ao consolidador**.

## Produz
- docs/epicos/runs/CONS-05-acumulador-ao-vivo.md
