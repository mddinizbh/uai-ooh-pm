# RECAL-01 — recompute `face_reach`/`line_reach` com fonte medida

> F2 (Bloco 2) · lane **recalibracao** · **Repo-alvo:** `uai-ooh-pipeline` · **Stack:** Python + PostgreSQL/PostGIS
> **Depende de:** CONS-04 (alguns dias de `medido.viagem_hex`) + Onda 2 ✅. · *(Lane nova — F2-#7)*

## Objetivo
Re-derivar **`face_reach`/`line_reach`** trocando a trajetória **estimada** (schedule GTFS) pela
**medida** (`medido.viagem_hex`) — **mesma fórmula, mesma superfície** (`core.exposure_cell` ×
`core.od_trip`), só muda a fonte de "por onde/quando o veículo passou". Agregar também o
**`v_real`** (`medido.parada_velocidade`) no modelo.

## Como executar
- **CLI:** estender o pipeline `reach` existente com `--fonte={estimada|medida}` (default `estimada` —
  zero impacto no fluxo atual): `python -m ooh_pipeline normalizer reach face-reach --fonte=medida`.
  *(Opção A do replanejamento: reusa `ooh_pipeline/normalizer/reach/face_reach.py`, ChunkedBuild,
  `model_params`, versionamento — não cria pipeline paralelo.)*
- **Fonte medida:** grão `(vehicle_code, h3_index, faixa_horaria)` agregado de
  `medido.viagem_hex ⨝ medido.viagem` (tipo_dia derivado de `service_date`; ponderar por nº de
  viagens medidas; `dwell_s` disponível pra refinar o peso de exposição).
- **`v_real`:** agregar `medido.parada_velocidade` → `core.pattern_stop_exposure.v_real` (mediana
  por parada/faixa) — **o pipeline escreve no `core`, nunca o consolidador** (F2-#3).
- Persistir o recompute **identificando a fonte** (coluna/versão `fonte=medida` em
  `face_reach`/`line_reach`) — estimado e medido coexistem pro lado-a-lado do RT-02; materializa no
  `serving` no swap atômico padrão.
- **Cadência: D-1** (madrugada, sobre o dia fechado). Convenções: SRID 31983, `statement_timeout`
  10min/chunk, core sempre BUILDING (`current_version_id`).

## Decisões
- **Mesma fórmula da Onda 2 — fonte única** (reconcilia com o acumulador ao vivo do CONS-05).
- **Cobertura parcial é esperada** no começo (só linhas/veículos com viagens medidas têm fonte
  medida); o recompute marca cobertura, não inventa pro resto.

## Critério de pronto (verificável no banco `ooh`)
- `face_reach`/`line_reach` com `fonte=medida` populados pras linhas com dado; `v_real` agregado;
  serving atualizado; job D-1 agendado no `uai-infra` (padrão dos crons do pipeline).

## Produz
- docs/epicos/runs/RECAL-01-recompute-fonte-medida.md
