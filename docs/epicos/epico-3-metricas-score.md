# Épico 3 — Métricas por linha: corredor, impressões e score

> Normalização F1, parte 3 (o coração). Cruza o corredor de cada linha com as camadas de contexto,
> computa demanda, impressões e o score 0–100, e materializa as tabelas planas que o app consome.
> Banco `ooh`, schema `core`, PostGIS. **Depende de:** Épicos 1 e 2.
> A **simulação da 4107** (`gen_ficha_4107.py`) é a prova de conceito de UMA linha; este épico
> generaliza para as **304 linhas** e adiciona a normalização do score.
>
> **Atualização (pós-plano de serviços) — modelo de alcance refinado:** o alcance EXTERNO deixou de
> ser população residencial a 300m. Agora: **E_pontos** (gente nos pontos do trajeto via embarque,
> faixa ~30m, modulada pela **velocidade de passagem** — a verificada vem do `trip-consolidator`) +
> **E_pop_frontagem** (residentes ~30m, área-ponderada, coef pequeno) + **E_traf** (trânsito, v2);
> interno = **E_pax** (passageiros, MCO). O **buffer 300m** fica **só p/ perfil (A3/A5) e prospecção**.
> Alcance é **por padrão de viagem**, agregado por tipo de dia **ponderado por frequência**.
> Precompute em `core.pattern_stop_exposure`. Proxies/honestidade: `proxies-e-premissas.md`.

> ⚠️ **Recalibração 2026-06-04 (pós-Épico 2 — decisões do dono).** O Épico 2 materializou o `core` e
> mudou 3 premissas deste épico (os alvos abaixo já estão corrigidos). Ver `runs/epico-2-contexto-2026-06-04.md`.
> 1. **`s_perfil` = quintil-de-BH** (classe relativa; global A–E ≈ 20% cada). O alvo %A+B do 4107 passa
>    de 43,8% (escala ABEP, que **não existe** no `core`) para **~82%**. ABEP fica v2 (sem insumo hoje).
> 2. **`s_poi` = UMA fonte primária (Overture), `relevancia_prospeccao>=1`, densidade por km** — **não**
>    a soma multi-fonte. Motivo: `core.poi.link_grupo_id` está **100% nulo** (cross-source não resolvido)
>    → somar fsq+overture dupla-conta (4107: ~40,8k bruto vs **18,8k** só-Overture). O `n_poi_total`
>    bruto multi-fonte é guardado, mas serve à **prospecção**, não ao score.
> 3. **Impressões F1 = fórmula simples (T3.5)**, SEM modular por velocidade de passagem (vem do
>    `trip-consolidator`, Épico 5 — inexistente em F1). `E_pontos`-por-velocidade e `E_traf` = v2.
>
> Princípio: **F1 ranqueia com o dado que existe hoje; onde falta dado, vira v2 declarado no ledger.**

**Índices na `raw` (criar ao rodar):** o Épico 3 cruza majoritariamente `core`×`core` (GiST/btree já criados no `core`); o único acesso pesado à raw é **3.4 demand** = btree `pbh__mco_consolidado(linha, tipo_dia)` + `ANALYZE`. Detalhe em `arquitetura-servicos.md` §"Índices na `raw`".

---

## Tarefa 3.1 — Corredor por linha × censo → demografia/renda
- Para cada `core.line`: `corredor = ST_Buffer(shape_representativo, 300)`. Intersectar com
  `core.census_sector`.
- **Lição da simulação — ponderar por área** (a 4107 deu 182.566 sem ponderar = superestima):
  `pop_ponderada = Σ populacao × ST_Area(ST_Intersection(setor,corredor))/ST_Area(setor)`.
  Guardar **ambos** (`pop_corredor` bruto e `pop_corredor_pond`) para transparência.
- `renda_media_pop = Σ(pop_pond×renda)/Σ pop_pond` (só setores com renda). Distribuição A–E:
  `Σ pop_pond por classe / total`. → grava `core.line_profile_demografico(line_id, classe_renda, pop, pct)` + colunas em `line_metrics`.
- **Validação 4107:** renda ≈ R$ 7.810 (+67% vs BH); **%AB ≈ 82% — escala quintil-de-BH** (relativa; A–E ≈ 20% cada em BH, ver Recalibração); a pop ponderada será menor que 182k. *(Alvo antigo 43,8% pressupunha ABEP absoluta — descartado.)*

## Tarefa 3.2 — POIs no corredor por categoria
- `ST_Within(poi.geom_31983, corredor)` filtrando **`fonte='overture'` (primária) + `relevancia_prospeccao>=1`**
  → `n_poi_alimentacao/comercio/educacao/saude/total` por linha + **`densidade_poi_km = n_poi_total/(comprimento_m/1000)`**.
- **Fonte única, não soma:** `link_grupo_id` 100% nulo → somar fsq+overture dupla-conta. Overture pela
  categorização/`confidence`. **Validar a escolha:** correlação de ranking Overture×FSQ (Spearman) — se alta, fica Overture.
- Guardar **`n_poi_total_prospeccao`** (união bruta multi-fonte fsq+overture+osm) por linha, **separado** —
  uso = prospecção/leads, **não** entra no score.
- **Validação 4107 (buffer 300m, corredor ≈ 16,0 km):** Overture `rel≥1` ≈ **18,8k POIs** (densidade ~1,17k/km).
  Soma bruta multi-fonte ≈ 40,8k (referência do quanto a dupla-contagem inflaria). *(Alvo antigo ~524 era OSM-only/pré-multi-fonte — descartado.)*

## Tarefa 3.3 — Exposição arterial por linha (componente de trânsito)
- `pct_arterial = ST_Length(ST_Intersection(shape, ST_Buffer(arteriais_proximas,20))) / ST_Length(shape)`,
  com `arteriais` = `core.road_segment WHERE classe_arterial='arterial'` filtradas por
  `ST_DWithin(shape,25)` (performance). Guardar `classe_via_predom`.
- **Validação 4107:** ≈ 30,6% (bate). Usar **shape representativo por sentido** (não o Collect).

## Tarefa 3.4 — Demanda por linha (MCO + embarque)
- `core.line_demand_daily(line_short_name, tipo_dia, n_viagens, total_usuarios, pax_por_dia)` ←
  `raw.pbh__mco_consolidado` agregado por `linha,tipo_dia` (8=útil,7=sáb,1=dom; volume set/2025).
- `core.line_boarding_profile(line_short_name, faixa_horaria 0..23, share_embarque)` ←
  `raw.pbh__embarque_ped_sublinha` (`c_0..c_23`/`total_geral`, normalizado por linha; **distribuição** mai/2024, não volume). Linha→short_name (~99%).
- **Validação 4107:** útil 3.616 pax/dia, sáb 1.544, dom 920 (bate).

## Tarefa 3.5 — Impressões (back bus) — F1
> **F1 usa esta fórmula auto-contida** (validada no `gen_ficha_4107`). **Não** modular `E_pop` por
> velocidade de passagem nem incluir `E_traf` — ambos dependem do `trip-consolidator` (Épico 5) e ficam **v2**.

Por linha × tipo_dia, **contatos visuais** (ilustrativo, ver honestidade):
```
E_pop(d) = pop_corredor_pond × f_exposicao(0.15) × freq_norm(d)      # freq_norm = viagens_dia(d)/viagens_dia(util)
E_pax(d) = pax_por_dia(d)    × p_vista_traseira(0.6)
Impressoes(d) = E_pop(d) + E_pax(d)        # E_traf = 0 em F1 (volume/congestionamento → v2)
```
Coeficientes parametrizáveis (tabela `core.model_params`). Mês = Σ Impressoes(d)×dias(d).
- **Validação 4107:** ≈ 29,6k/dia útil, ~776k/mês (bate com `gen_ficha_4107.py`).
- **Marcar `faixa_indicativa_pct=35` e `metodo_versao`** em toda linha; `E_traf` documentado como v2.

## Tarefa 3.6 — Score 0–100 (normalização entre as 304 linhas)
- **Só aqui precisa de TODAS as linhas** (a simulação de uma linha não dá o score). Sub-scores por
  min-max sobre o conjunto:
  - `s_alcance` = norm(impressoes_util) · `s_densidade` = norm(pop_pond/comprimento_km)
  - `s_perfil` = norm(pct_pop_AB **quintil-de-BH**) *(invertível por campanha — A5)* · `s_arterial` = norm(pct_arterial)
  - `s_poi` = norm(**densidade_poi_km** — Overture `rel≥1`, **não** a contagem bruta multi-fonte)
- `score_total = 100×(0.35·s_alcance + 0.15·s_densidade + 0.20·s_perfil + 0.20·s_arterial + 0.10·s_poi)`.
  Pesos em `core.model_params`. Guardar os 5 sub-scores (re-rank por pesos em runtime no app, sem PostGIS).
- **Validação:** score sem nulos; top/bottom 10 linhas fazem sentido (linhas de eixo nobre + alta
  demanda no topo); 4107 num patamar coerente (corredor rico, demanda média).

## Tarefa 3.7 — Materializar saída + ativar versão
- `core.line_metrics` (1 linha por `core.line`): pop bruto/pond, renda_media_pop, classe_predom,
  pct_AB/DE, n_poi_*, pct_arterial, classe_via_predom, pax_{util,sab,dom}, impressoes_{util,sab,dom}
  (+ componentes E_pop/E_pax), faixa_indicativa_pct, score_total + 5 sub-scores, metodo_versao, computed_at, dataset_version.
  POI: `n_poi_*` + `densidade_poi_km` (Overture rel≥1, entra no score) + `n_poi_total_prospeccao` (bruto multi-fonte, só prospecção). `pct_AB/DE` = quintil-de-BH.
- `core.line_profile_demografico` (linha×classe A–E).
- Virar `core.dataset_version` para `ACTIVE`.
- **Pronto:** 304 linhas com métricas e score; tabelas planas prontas para export (Épico 4).

---

## Critério de pronto do Épico 3
- `core.line_metrics` e `core.line_profile_demografico` populadas para as 304 linhas, validadas
  contra os números da 4107 (renda 7.810 / **%AB ~82 quintil-BH** / arterial 30,6 / **~18,8k POIs Overture rel≥1** / 29,6k impressões).
- Score 0–100 normalizado entre linhas, com 5 sub-scores guardados e pesos parametrizáveis.
- Honestidade carimbada: impressões = ilustrativas (ranking vendável, absoluto não), faixa ±35%,
  `E_traf` = v2, pop ponderada por área.
- **Habilita o Épico 4** (servir ao produto via app Java).
