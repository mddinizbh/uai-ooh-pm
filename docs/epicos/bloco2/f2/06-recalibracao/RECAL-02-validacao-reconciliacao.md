# RECAL-02 — validação e reconciliação (4107)

> F2 (Bloco 2) · lane **recalibracao** · **Repo-alvo:** `uai-ooh-pipeline` · **Stack:** Python + SQL (MCP `postgres-ooh`)
> **Depende de:** RECAL-01. **Fecha o critério de aceite do F2.** · *(Lane nova — F2-#7)*

## Objetivo
Provar que o loop verificado→estimado fecha: o medido é plausível, reconcilia com o ao vivo, e a
faixa de incerteza **estreita de fato**.

## Validações (linha 4107 + os 5 carros-fixture `11198/20736/30835/40705/40806` — ver techspec do consolidador §Fixture; registrar counts reais no run)

> **Local-first (2026-06-10):** esta validação roda **primeiro contra o ambiente LOCAL** (LOCAL-01,
> psql/redis-cli locais — parte `e2e` do workflow, disparo manual). O re-check em **prod** é a parte
> `verify-prod` (via MCP `postgres-ooh`), D+1 do deploy — mais leve, só confirma que o agendado
> (INFRA-04) produz o mesmo que o manual produziu no local.
1. **Plausibilidade física:** viagens/dia medidas ~ frequência GTFS; km ~ extensão × viagens;
   cobertura `viagem_hex` contígua ao corredor (sem buracos não explicados / sem hexes fora).
2. **Medido vs estimado:** `face_reach`/`line_reach` fonte medida vs estimada — desvios por linha
   explicáveis (frequência real ≠ schedule, velocidade real ≠ assumida); faixa antes/depois quantificada.
3. **Reconciliação incremental↔agregado:** Σ `impressoesParciais` ao vivo (CONS-05) ≈ agregado do
   fechamento (mesma viagem) — |Δ| pequeno e explicado (ping vs interpolado).
4. **Selo (ADR-058):** trajetória/frequência/velocidade marcadas MEDIDO; reach/impressões seguem
   ESTIMATIVA (visada cega) em todo payload/doc — auditar RT-02/WEB-02.
5. **Identidade (F2-#8):** % de `vehicle_code` do `medido` presentes em `core.vehicle` (esperado
   ~85–90%; suplementares fora) — registrar pro futuro reconcílio de cadastro.

## Critério de pronto
- Run com counts reais + desvios e explicações; faixa antes/depois documentada; pendências viram
  cards (não silenciar). **→ F2 aceito quando 1–3 passam na 4107.**

## Produz
- docs/epicos/runs/RECAL-02-validacao-reconciliacao.md
