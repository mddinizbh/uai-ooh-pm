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
- **Validação 4107:** renda ≈ R$ 7.810 (+67% vs BH), %AB ≈ 43,8%, %DE ≈ 9,7% (bate com a sessão; a pop ponderada será menor que 182k).

## Tarefa 3.2 — POIs no corredor por categoria
- `ST_Within(poi.geom, corredor)` → `n_poi_alimentacao/comercio/educacao/saude/total` por linha.
- **Validação 4107:** ~524 POIs (ali 370 / sau 58 / edu 49 / com 47), bate com a ficha.

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
  - `s_perfil` = norm(pct_pop_AB) *(invertível por campanha — A5)* · `s_arterial` = norm(pct_arterial)
  - `s_poi` = norm(n_poi_total)
- `score_total = 100×(0.35·s_alcance + 0.15·s_densidade + 0.20·s_perfil + 0.20·s_arterial + 0.10·s_poi)`.
  Pesos em `core.model_params`. Guardar os 5 sub-scores (re-rank por pesos em runtime no app, sem PostGIS).
- **Validação:** score sem nulos; top/bottom 10 linhas fazem sentido (linhas de eixo nobre + alta
  demanda no topo); 4107 num patamar coerente (corredor rico, demanda média).

## Tarefa 3.7 — Materializar saída + ativar versão
- `core.line_metrics` (1 linha por `core.line`): pop bruto/pond, renda_media_pop, classe_predom,
  pct_AB/DE, n_poi_*, pct_arterial, classe_via_predom, pax_{util,sab,dom}, impressoes_{util,sab,dom}
  (+ componentes E_pop/E_pax), faixa_indicativa_pct, score_total + 5 sub-scores, metodo_versao, computed_at, dataset_version.
- `core.line_profile_demografico` (linha×classe A–E).
- Virar `core.dataset_version` para `ACTIVE`.
- **Pronto:** 304 linhas com métricas e score; tabelas planas prontas para export (Épico 4).

---

## Critério de pronto do Épico 3
- `core.line_metrics` e `core.line_profile_demografico` populadas para as 304 linhas, validadas
  contra os números da 4107 (renda 7.810 / %AB 43,8 / arterial 30,6 / 524 POIs / 29,6k impressões).
- Score 0–100 normalizado entre linhas, com 5 sub-scores guardados e pesos parametrizáveis.
- Honestidade carimbada: impressões = ilustrativas (ranking vendável, absoluto não), faixa ±35%,
  `E_traf` = v2, pop ponderada por área.
- **Habilita o Épico 4** (servir ao produto via app Java).
