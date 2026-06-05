# EP2-06 — intel: regiões + filtros geográficos

> Bloco 1 (uAI-OOH F1) · **Épico EP2** (intel) · card 6 do kanban — **endpoint/filtros NOVOS**.
> **Depende de:** EP2-02 + EP2-03 (leitura) + **EP1 / T8b** (regionalização no serving) · **Destrava (front):** EP4-02 (filtros/cascata) · **Paralelizável:** parcial
> **⚠️ GATE:** precisa de `serving.line_area` (linha→regional/bairro) + taxonomia regional/bairro materializados pelo **T8b**. Codifica contra o schema; teste fim-a-fim fecha com o T8b aplicado.
> **Repo-alvo:** `uai-ooh-intel` · **Stack:** Java 21 / Spring Boot
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §5.2/§6/§12.

## Objetivo
Servir a **taxonomia região→bairro** (pra cascata do front) e **filtrar** as linhas por área. **Sem `ST_*`** (a classificação espacial já foi feita no T8b; aqui é SELECT plano).

## Endpoints / filtros
| Verbo · path | Retorno | Fonte |
|---|---|---|
| `GET /api/regions` | `[{ regiao, bairros[] }]` — as 9 regionais + bairros de cada | `serving` (regional + bairro do EP1) |
| `GET /api/lines?regiao=&bairro=` | linhas que **servem** a área | `serving.line_area` |
| `GET /api/lines/ranking?…&regiao=&bairro=` | idem, no ranking | `serving.line_area` |

- **Semântica:** linha "serve/atravessa" a região = tem ≥1 ponto nela (de `line_area`); uma linha cruza várias regiões.
- **Cascata:** `regiao` selecionada → `bairros` carregam de `/api/regions`; `bairro` é filho de `regiao`.

## Decisão
- **Re-normalização do score sob filtro: BH-relativo** (decidido 2026-06-05) — normaliza o score entre as **303** e filtra as linhas exibidas depois; o score de uma linha **não muda** por causa do filtro (comparável sempre).

## Critério de pronto (verificável)
- `GET /api/regions` devolve as **9 regionais** + bairros (cascata).
- `/api/lines` e `/ranking` filtram por `regiao`/`bairro` (linhas que servem a área).
- Re-normalização **BH-relativo** (default).
- Validado contra `serving.line_area` do EP1 (T8b aplicado).

## Produz
- docs/epicos/runs/EP2-06-regioes-filtros.md
