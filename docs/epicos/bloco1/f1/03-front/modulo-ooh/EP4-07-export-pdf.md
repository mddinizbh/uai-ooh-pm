# EP4-07 — front: export PDF (ProposalPort)

> Bloco 1 (uAI-OOH F1) · **Épico EP4** (front) · card 7 do kanban.
> **Depende de:** EP4-06 (cesta) + EP4-01 (`ProposalPort`) + **EP4-09** (snapshot do mapa da cesta) · **Paralelizável:** parcial
> **Repo-alvo:** `uai-spark` (→ uai-portal) · **Stack:** React 18 · TS
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §5.5/§10.

## Objetivo
O **"modo proposta" client-side** — fecha o workflow *explora → cesta → **export***. Gera um PDF da cesta como proposta, via `ProposalPort.export()` (adapter local F1).

## Conteúdo do PDF
- **Capa** (cliente, BH Bus, data) + **linhas da cesta** (ficha resumida cada) + **COMBINADO** + **🗺️ mapa da cesta** (snapshot PNG da cobertura, de **EP4-09**) + **disclaimer de honestidade** (impressões = estimativa ±35% / OTS, não pessoas únicas; ranking é o defensável; vigências).

## Decisão
- **Modo proposta + print** (decidido/recomendado p/ F1): uma view "modo proposta" imprimível + `react-to-print`/`window.print()` → salvar como PDF. Quase zero dep. É um **stopgap** até o agent+template — **não super-investir**. *(Alternativa: lib `html2pdf`/`jspdf` — download em 1 clique, +1 dep.)*

## Evolução (pós-F1, PRD §10)
- A geração migra pra um **endpoint que chama um agent** com o **template-padrão de proposta da empresa** → **troca o adapter** da `ProposalPort`, sem mexer no resto. Liga o OOH ao **motor de agentes uAI** (`marketing-agency`/`midia-core`).

## Critério de pronto
- Botão exportar (da cesta) gera o PDF "modo proposta": capa + linhas + COMBINADO + disclaimer.
- Via `ProposalPort.export()` (adapter local F1) — client-side, sem backend.
- **Disclaimer de honestidade sempre presente** no PDF.

## Produz
- docs/epicos/runs/EP4-07-export-pdf.md
