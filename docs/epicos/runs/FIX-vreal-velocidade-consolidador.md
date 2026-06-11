# FIX — v_real do consolidador (Δfração-snap → distância GPS) — 2026-06-11

> **Repo-alvo:** `uai-ooh-trip-consolidator` (`PostgisTripMetricsCalculator`). Bug descoberto ao rodar
> RECAL-01 end-to-end (v_real agregado dava mediana 450 km/h). Debugging sistemático → causa-raiz →
> fix → verificado em 2 níveis (unit + integração ao vivo).

## Sintoma
`medido.parada_velocidade.v_real` bruto: **mediana 450 km/h, p95 2.601, máx 5.889; 60% acima de 80**.
Valores absurdos e repetidos (5889×3, 5138×3). Ônibus não faz 5.889 km/h.

## Causa-raiz (por evidência, não palpite)
- **Δt está saudável** (mediana 52s entre pontos do track, mín 20s, zero abaixo de 10s) → não é o tempo.
- A fórmula usava **distância = `(Δfração_snap × comprimento_shape)`**. O `ST_LineLocatePoint` projeta cada
  ponto no ponto mais próximo do shape; em **shape auto-sobreposto** (ida-e-volta/loops — comum em corredor
  de ônibus) pontos consecutivos snapam em "passadas" diferentes → **Δfração salta** (km de diferença em ~50s).
- Um segmento com salto tem `[lo,hi]` enorme → **ladeia muitas paradas**, carimbando todas com a mesma
  velocidade absurda (explica os valores repetidos + a mediana inflada, não só a cauda).
- **Prova direta:** a viagem com v_real=5.889 km/h (via Δfração) tem velocidade **GPS real de 20 km/h** (avg).

## Fix (`feat/ooh-recal-01`? não — repo do consolidador, NÃO commitado)
`PostgisTripMetricsCalculator.speedAtFraction`: a fração-snap continua **localizando** o segmento que
ladeia a parada, mas a **distância** passa a ser a **haversine GPS** entre os 2 pontos consecutivos
(imune ao ruído de snap). `Snapped` carrega `lat/lon`; novo helper `haversineMeters`. Javadocs da classe
e de `StopSpeed` atualizados.

## Verificação (2 níveis)
- **Unit:** `PostgisTripMetricsCalculatorTest` (3/3 verde) — haversine: 0,001°lat≈111,32m; segmento BH
  ~600m; mesmo-ponto=0.
- **Integração AO VIVO** (rebuild + restart do consolidador, 407 paradas de viagens fechadas pós-corte):

  | Métrica | Antes (lixo) | Depois (fix) |
  |---|---|---|
  | mediana | 450 km/h | **26,5 km/h** |
  | p95 | 2.601 | 41,2 |
  | máx | 5.889 | 142 |
  | % > 80 km/h | 60% | 2% (8/407) |

  Velocidade de ônibus plausível; cauda pequena (máx 142) = ruído GPS/trecho rápido, não o bug sistemático.

## Higiene de dados (FEITO, 2026-06-11)
- `DELETE` de **28.263 linhas pré-fix** do `medido.parada_velocidade` (corte `ended_at <= T0=18:52:56`,
  o restart com o fix); mantidas **5.299 pós-fix** (mediana 19,7 km/h).
- `DELETE` de `core.pattern_stop_exposure WHERE version_id=1` (o upsert deixaria paradas órfãs com o lixo).
- Re-run `normalizer reach face-reach --fonte=medida` → **v_real re-agregado LIMPO**: 3.013 paradas,
  **mediana 449→19,2 km/h, máx 4.710→100,4, >80km/h 0,2%** (6/3.013).
- **Bônus:** a re-rodada expandiu a cobertura medida (consolidador acumulou): 448→**680 veículos**,
  `face_reach` medida 1.773→**2.698 faces**. `line_reach` medida ficou levemente stale (só re-rodei
  face-reach) — o cron D-1 do `--fonte=medida` completo reconcilia.

## Pendências derivadas
1. **Commitar** o fix (consolidador) + o fix do `line_reach.py` (pipeline) + o serviço no compose (infra).
2. A pendência antiga (clamp ~70 km/h) fica **obsoleta** — não era outlier, era cálculo errado.
