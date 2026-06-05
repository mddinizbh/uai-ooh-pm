# EP2-05 — intel: agregação da cesta (POST /aggregate)

> Bloco 1 (uAI-OOH F1) · **Épico EP2** (intel) · card 5 do kanban — **endpoint NOVO** (não existia no épico-4).
> **Depende de:** EP2-02 (domínio) + EP2-03 (leitura/JdbcTemplate) · **Destrava (front):** EP4-06 (cesta + combinado) · **Paralelizável:** parcial
> **Repo-alvo:** `uai-ooh-intel` · **Stack:** Java 21 / Spring Boot
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §5.4/§6.

## Objetivo
`POST /api/lines/aggregate` — recebe `lineIds[]` (+ `publicoAlvo` opcional) e devolve o **combinado da cesta** + **per-linha**. **Stateless, em memória, sem persistência**, sem `ST_*`.

## Regras de combinação
- **impressões combinadas** = `Σ impressoes_util/sab/dom/mes` — **OTS somado** (contatos visuais).
- **pax combinado** = `Σ pax_util/sab/dom`.
- **%AB combinado** = `Σ(pct_ab · impressões) / Σ(impressões)` — ponderado pela exposição (idem `%DE`/classe).
- **faixa ±35%** carrega pro combinado.
- **per-linha**: devolve as métricas-chave de cada linha (a cesta lista).

## Decisão
- **Alcance/`pop_corredor` combinado: NÃO somar** (decidido 2026-06-05) — impressões (OTS) é o "tamanho" combinado; não apresenta residentes somados (corredores se sobrepõem → evita o trap de "alcance único"). `pop` fica só por-linha.

## Honestidade (crítico)
- Combinado = **OTS somado**, **nunca alcance único**; sobreposições não descontadas; **sem dedup** (matriz O-D = pós-F1).
- **Sem "score combinado"** — o score é relativo por linha; a cesta mostra alcance/perfil combinados, não um 0–100 agregado.
- Payload marca explicitamente impressões como **estimativa ±35% / OTS**.

## Exemplo (real — cesta [62, 9250, 4107])
- impressões ~**86.952**/dia · pax **22.135**/dia · **%AB ponderado ~68%**.

## Critério de pronto (verificável)
- `POST /aggregate` com N `lineIds` devolve combinado correto (Σ impressões, Σ pax, %AB ponderado) + per-linha.
- `publicoAlvo` aplica a mesma lente do ranking (consistência com EP2-04).
- Stateless, em memória, sem persistência; honestidade marcada no payload.

## Produz
- docs/epicos/runs/EP2-05-agregacao.md
