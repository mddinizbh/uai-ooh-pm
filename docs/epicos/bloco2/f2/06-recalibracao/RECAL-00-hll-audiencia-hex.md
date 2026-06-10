# RECAL-00 — HLLs de audiência por hexágono no Redis (insumo do alcance ao vivo)

> F2 (Bloco 2) · lane **recalibracao** · **Repo-alvo:** `uai-ooh-pipeline` · **Stack:** Python + Redis (PFADD)
> **Destrava:** CONS-05 (alcance ao vivo com dedup). **Criada em 2026-06-10** (decisão do dono: alcance subindo ao vivo — o carro fora da rota pode alcançar mais que o estimado da linha).

## Objetivo
Pré-computar, da base OD, um **HyperLogLog por (hex × tipo_dia × faixa_horaria)** com os
`id_usuario` da audiência do hex, e materializar no **Redis** (`hll:hex:{h3}:{tipoDia}:{faixa}`).
O consolidador (CONS-05) faz `PFMERGE` desses sketches por hex visitado → **alcance ao vivo com
dedup real** (~±0,8%), sem tocar banco no hot path.

## Como executar
- Job do pipeline (padrão roda-e-sai): varre `core.od_trip` (1,2M viagens; cada uma conta em
  `h3_origem` e `h3_destino` na sua `faixa_horaria`/`tipo_dia`) → `PFADD hll:hex:... id_usuario`.
  ~2,4M PFADDs em lote (pipeline Redis) — minutos.
- Rodar **após cada rebuild da base OD** (mesmo gatilho do `normalizer reach`); chaves versionadas
  ou flush+rebuild (job é idempotente e rápido). **A fiação do gatilho no `uai-infra` é da INFRA-04**
  (este card entrega o comando; a INFRA-04 o encadeia no workflow).
- **Medir memória total no Redis** (188k chaves, maioria sparse — audiência média ~136 por
  hex×faixa): registrar no run. Se estourar o orçamento da VPS: carregar só hexes com audiência > 0,
  reduzir grão (hex × tipo_dia, sem faixa) ou HLL blobs em Postgres com cache lazy — decidir com o
  número real na mão.

## Decisões
- **HLL nativo do Redis** (PFADD/PFMERGE/PFCOUNT, erro padrão ~0,81%) — zero dependência nova.
- O **exato** continua sendo o SQL do RECAL-01/02 (D-1, dedup verdadeiro) — o HLL é só o ao vivo.

## Critério de pronto
- Chaves `hll:hex:*` populadas; spot-check: `PFCOUNT` de hexes conhecidos ≈ `unicos_hora` do
  `exposure_cell` (±2%); memória medida e registrada; job idempotente re-rodável.

## Produz
- docs/epicos/runs/RECAL-00-hll-audiencia-hex.md
