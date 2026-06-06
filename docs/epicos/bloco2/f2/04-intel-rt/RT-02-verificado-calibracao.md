# RT-02 — verificado por linha + loop de calibração

> F2 (Bloco 2) · lane **intel-rt** · **Repo-alvo:** `uai-ooh-intel` *(estende F1)* · **Stack:** Java/Spring
> **Depende de:** `core.trip_executed` + `pattern_stop_exposure.v_real` (CONS-04). **É o produto central do F2.**

## Objetivo
Expor o **verificado por linha** (do `trip_executed` + `v_real`) **lado a lado com o estimado** do F1 — o loop **verificado→estimado** que valida/estreita a ±35%.

## Como executar
- `GET /api/lines/{id}/verified` → **viagens/dia reais**, km, completude média, **velocidade real média** (de `trip_executed`/`v_real`).
- **Comparação:** colocar ao lado do estimado do F1 — frequência GTFS vs real, velocidade assumida vs `v_real`, e **o quanto a faixa ±35% se estreita** com o medido.
- **Alcance recalculado:** reaplica a **fórmula de alcance do F1** (`serving.line_metrics` + corredor) **substituindo a velocidade pelo `v_real`** → alcance/impressões "com velocidade e frequência reais". Reage ao **`ooh.trip.completed`** pra frescura.
- Leitura plana do `core`/`serving` (sem `ST_*` — agrega counts/médias).

## Decisão / fronteira
- **F2 expõe a comparação** (verificado vs estimado). A **recalibração efetiva do score** (atualizar `model_params`/coeficientes a partir do `v_real`) é um passo do **normalizer/pipeline**, não do intel.
- **Aqui = AGREGADO por linha** (read-time, reage ao evento). O **acumulador de alcance ao vivo** (por-posição, incremental, "subindo em tempo real") é **pós-F2**, num **módulo de stream separado** — não o consolidador (fatos) nem o hot-path do intel. **Mesma fórmula → reconcilia** (soma incremental ≈ este agregado). Ver `../README.md` §Futuro.
- ⚠️ **Continua estimativa:** só a velocidade vira real; coeficientes não calibrados.

## Critério de pronto (verificável)
- `GET verified?line=4107` → números reais (viagens/dia, km, velocidade) + comparação com o estimado; a faixa ±35% comentada vs o real. **Verificado marcado como sólido (medido).**

## Produz
- docs/epicos/runs/RT-02-verificado-calibracao.md
