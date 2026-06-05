# EP2-04 — intel: ranking (re-rank por pesos + público-alvo)

> Bloco 1 (uAI-OOH F1) · **Épico EP2** (intel) · card 4 do kanban.
> **Depende de:** EP2-02 (domínio) + EP2-03 (leitura/JdbcTemplate) · **Destrava (front):** EP4-02 (lista/ranking) · **Paralelizável:** parcial (com EP2-03)
> **Repo-alvo:** `uai-ooh-intel` · **Stack:** Java 21 / Spring Boot
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §6; `epico-4-serving-app.md` (A4/A5).

## Objetivo
`GET /api/lines/ranking?weights=&publicoAlvo=AB|DE` — recalcula o score **em memória** sobre os 5 sub-scores, com pesos custom + **recalibração do `s_perfil` pelo público-alvo**, re-normalizado 0–100. **Sem PostGIS.**

## Como funciona
1. Carrega **todas** as linhas (sub-scores `s_alcance`/`s_densidade`/`s_perfil`/`s_arterial`/`s_poi` + `pct_ab`/`pct_de`) do `serving` (`dataset_version` ACTIVE) via JdbcTemplate.
2. **`s_perfil'` por público:** AB → `min-max(pct_ab)` · DE → `min-max(pct_de)` · sem público → `s_perfil` base.
3. `score' = Σ wᵢ·subscoreᵢ` (com `s_perfil'`). **Pesos** = default de `model_params` (0.35/0.15/0.20/0.20/0.10 — confirmar o mapeamento peso↔componente) ou os do query (**normalizados** se vierem parciais).
4. **Re-normaliza min-max 0–100** entre as linhas → ordena desc → `posicao`.
5. Tudo em memória, sem `ST_*`.

## Decisão
- **Público-alvo SUBSTITUI o `s_perfil`** (decidido 2026-06-05): `s_perfil'` = aderência ao público (AB→`pct_ab`, DE→`pct_de`), re-normalizada. Semântica clara: "ranking pra AB" = quem tem mais público AB sobe.

## Parâmetros
- `weights` — 5 pesos opcionais (default `model_params`); normalizar se a soma ≠ 1.
- `publicoAlvo` — opcional `AB|DE`; ausente = `s_perfil` base.
- `regiao`/`bairro` — **entram no EP2-06**. **Escopo da re-normalização (decisão p/ EP2-06):** manter **BH-relativo** (normaliza entre todas as 303, filtra as linhas exibidas depois) — o score de uma linha não muda por causa do filtro.

## Critério de pronto (verificável)
- `GET /ranking` sem params = **mesma ordem do score base** (sanity).
- `weights` alteram a ordem; `publicoAlvo=AB` sobe linhas de alto `%AB`, `=DE` sobe alto `%DE`.
- Score 0–100 re-normalizado entre as linhas; `posicao` correta; em memória, sem `ST_*`.

## Produz
- docs/epicos/runs/EP2-04-ranking.md
