# RT-02 — verificado por linha (medido vs estimado, selo por métrica)

> F2 (Bloco 2) · lane **intel-rt** · **Repo-alvo:** `uai-ooh-intel` *(estende F1)* · **Stack:** Java/Spring
> **Depende de:** `medido.viagem`/`parada_velocidade` (CONS-04) + `face_reach`/`line_reach` **fonte medida** no serving (RECAL-01, lane 06). **É o produto central do F2.** · *(Reescrito 2026-06-10 — era "reaplica a fórmula F1 com v_real", pré-face-centric)*

## Objetivo
Expor o **verificado por linha** lado a lado com o **estimado** — o loop que mostra a faixa de
incerteza **estreitando** quando trajetória/frequência/velocidade viram **reais**.

## Como executar
- `GET /api/lines/{id}/verified` →
  - **do `medido` (read-only):** viagens/dia reais, km, completude média, velocidade real média;
  - **do `serving` (recomputado pela lane 06):** `face_reach`/`line_reach` **fonte medida** vs **fonte estimada** — mesma fórmula da Onda 2, só muda a trajetória;
  - **comparação:** frequência GTFS vs real · velocidade assumida vs `v_real` · reach/impressões estimado vs medido.
- **Selo de confiança por métrica (ADR-058)** no payload — substitui o boolean `aindaEstimativa`:
  trajetória/frequência/velocidade = **medidas**; reach segue "audiência do corredor"; coeficientes de
  **visada continuam cegos** (só Fase C calibra) → o reach recomputado **continua estimativa**, com faixa menor.
- **Frescura:** cache por linha invalidado pelo **`ooh.trip.completed`** (consumer no intel) — não recalcula por request.
- Leitura plana (sem `ST_*` — ADR-003).

## Decisões / fronteira
- O intel **expõe** a comparação; o **recompute** é da lane 06 (pipeline, ADR-050) — o intel nunca re-deriva o modelo.
- O acumulado **ao vivo** é o RT-01/CONS-05; aqui é o **agregado consolidado** — mesma régua, reconciliam.

## Critério de pronto (verificável)
- `GET verified?line=4107` → números reais + comparação + selo por métrica; atualiza ao fechar viagem; IT com seed de `medido`/serving.

## Produz
- docs/epicos/runs/RT-02-verificado-calibracao.md
