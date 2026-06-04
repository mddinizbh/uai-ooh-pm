# Run — Épico 2 (Contexto): T8–T10 · 2026-06-04

> Execução **observada** no `uai-ooh-pipeline`, branch `feat/ooh-epico2-contexto` (commit `d1631a5`).
> Código por workflow multi-agente (ultracode); execução no banco + validação no loop principal.
> Estado **verificado no banco `ooh`** (schema `core`) via MCP `postgres-ooh` em 2026-06-04 — bate 100%.
> Run completo (fixes, queries, perfil 4107): vault → `repositorios/uai-ooh-pipeline/docs-epicos/runs/epico2-contexto.md`.

## Origem da evidência
- Pipeline nova `ooh_pipeline/normalizer/contexto/` (molde de `identidades/`, reusa `ChunkedBuild`):
  `census_sector` · `poi` · `road_segment` · `run` · `validate` · `_support`. Registrada em `__main__`.
- Ingestor `load_poi_overture_fsq.py` estendido (Overture+FSQ): endereço, contato, brand, confidence.
- Executado contra `dataset_version=1` (status **BUILDING**, reusada — sem nova versão).

## Estado materializado no `core` (counts reais)

| Task | Tabela(s) `core` | Linhas | Detalhe | Veredito |
|---|---|---|---|---|
| T8 (2.1) | `census_sector` | **5.166** | A/B/C/D/E ≈ 20% cada (5.113 c/ classe; 53 sem renda); renda BH R$ 4.682,30; classe = quintil-de-BH | ✅ |
| T9 (2.2) | `poi` | **248.353** | overture 113.814 · fsq 132.061 · osm 2.478 — **união marcada por fonte, SEM dedup (ADR-051)** | ✅ |
| T9 (2.2) | `poi_taxonomy` · `poi_category_map` | 26 subgrupos / 9 grupos | de-para versionável; ruído (relevancia=0) = 14.077 | ✅ |
| T10 (2.3) | `road_segment` | **55.143** | arterial 6.147 (11,1%) · coletora 29.726 (53,9%) · local 19.270 (34,9%) | ✅ |

- `validate` (`normalizer contexto validate`) → **exit 0**; gates críticos OK.
- Perfil cruzado 4107 (buffer 150 m): renda corredor R$ 7.682 (116 setores) · exposição arterial 23,5%.

## Decisões / desvios (registrados, não-bugs) — impactam o Épico 3
1. **Classe de renda = quintil-de-BH (relativa), não ABEP absoluta** → corredor 4107 deu **%A+B = 82%**
   (alvo antigo 43,8% pressupõe escala absoluta; não comparável). Check virou informativo.
2. **POIs no corredor 4107 = 14.948** (não ~524): consequência do **ADR-051** (multi-fonte sem dedup).
   Alvo ~524 é pré-multi-fonte. Check informativo.
3. **Exposição arterial 4107 = 23,5%** (alvo ~30,6%): dentro da faixa; proxy tipo+largura.
4. **Porte/faturamento de POI ADIADO para v2** (decisão do dono) — `core.poi` de F1 sem coluna de porte.
5. **`link_grupo_id` 100% nulo (verificado no banco 2026-06-04)** — o cross-source link por sinal forte
   (etapa 4 do pipeline POI) **não foi populado**. Impacto: (a) pro **score**, somar fontes dupla-conta
   (4107: ~40,8k bruto vs 18,8k só-Overture) → o Épico 3 usa **fonte única**; (b) pra **prospecção**,
   ainda **não há dedup de leads** (mesmo dono pode aparecer 2-3×).
   **Pendência:** popular `link_grupo_id` por telefone/website/endereço normalizado iguais (sinal forte).

## Estado do `dataset_version`
- **1 / BUILDING** — permanece BUILDING até o fim do Bloco 1 (o `ChunkedBuild` resolve por
  `status='BUILDING'`; o Épico 3 reusa a v1). Promove a **ACTIVE** só ao fechar T17.

## Próximo
- Épico 3 (Métricas/Score) — T11–T16 + T17 (materializar `serving`). ⚠️ **Recalibrar os alvos de
  validação do Épico 3** antes de codar: os desvios 1 e 2 acima contradizem os alvos escritos no
  `epico-3-metricas-score.md` (%AB 43,8% e ~524 POIs). `serving.*` ainda vazio.
