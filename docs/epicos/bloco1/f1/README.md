# F1 — Planejamento OOH por score (entrega 1)

> Mapa de execução da **primeira entrega** do vertical uAI-OOH: a ferramenta **interna** de planejamento
> de mídia OOH por **score/ranking** das linhas de ônibus de BH (explorar → cesta → exportar proposta).
> **Fonte da verdade do produto:** `docs/prd/2026-06-05-ooh-intel-front-f1.md`.
> **Dados ✅ até o `serving`** — o pipeline base (T1–T18) está concluído e **arquivado** em `../_arquivo/`.
> Este `f1/` é o **mapeamento fresco** do que falta executar.

## Lanes (cada task é single-repo)

| Lane | Tipo | Repo | Tarefas |
|---|---|---|---|
| [`01-pipeline-dados/`](01-pipeline-dados/) | 🟫 **DATA** | `uai-ooh-pipeline` (Python+PostGIS) | T8b · EP1-02 |
| [`02-intel-backend/`](02-intel-backend/) | 🟦 **BACK** | `uai-ooh-intel` (Java/Spring) | EP2-01 … EP2-09 |
| [`03-front/`](03-front/) | 🟪 **FRONT** | `uai-spark` → `uai-portal` (React) | EP3-01…03 (shell) · EP4-01…08 (módulo OOH) |

## Ordem macro & gates
- **EP1 (dados) ∥ EP2 (intel) ∥ EP3 (shell) → EP4 (front, junta tudo).**
- **Gates:** EP2-06 (filtros) e EP4 (mapa/filtros) dependem do **EP1**; **EP3-02 (login)** depende do estado do **`uai-auth`** (SSO/introspection — a confirmar).

## Decisões travadas (resumo executável)
regenerar-do-template · dev local · **enum** (rótulos) · **JdbcTemplate** · público **SUBSTITUI** s_perfil · ranking **BH-relativo** · agregação **OTS** (não soma pop) · auth **introspection** sem tenant · **spark→uai-portal** · login **SSO redirect** · **fetch+react-query** · master-detail **lista-slim** · sub-scores **radar** · cesta **barra+drawer** (combinado sempre visível) · export **print-mode** (→ agent+template pós-F1).

## Honestidade (diferencial do produto)
score/ranking = **vendável**; impressões = **estimativa ±35% OTS**; combinado = **OTS somado, não alcance único**. Ledger: `docs/proxies-e-premissas.md`.

## Arquivado (`../_arquivo/`)
Pipeline de dados já entregue (T1–T18, **done**) + `plano-execucao.md` antigo.
**Redirects:** **T19 → EP2** · **T20a → EP2-09** · **T20b → EP4**.
