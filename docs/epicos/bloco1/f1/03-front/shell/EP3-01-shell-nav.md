# EP3-01 — plataforma: shell + nav de verticais (adapta o uai-spark)

> Bloco 1 (uAI-OOH F1) · **Épico EP3** (plataforma) · card 1 do kanban.
> **Depende de:** decisão "spark vira shell" (2026-06-05) · **Destrava:** EP4 (módulo OOH mora aqui) · **Paralelizável:** com EP2
> **Repo-alvo:** `uai-spark` (~/IdeaProjects/uai-spark → promovido a shell) · **Stack:** React 18 · TS · Vite · Tailwind · shadcn/ui
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §5.1; consulta ao `uai-spark` (2026-06-05).

## Objetivo
Usar o `uai-spark` como **shell da plataforma** e habilitar a **nav de verticais**, com **OOH como 1º vertical**. Reaproveita o shell que já existe; o módulo OOH (EP4) pluga como rota.

## Reusa do spark (pronto)
- `ProtectedLayout` (sidebar + main + guard de rota).
- Router data-driven (rotas protegidas + `/login` + NotFound).
- `shadcn/ui` completo + tema/branding uAI.

## Adapta / cria
- `PortalSidebar` → estrutura de **verticais** (hoje a nav é marketing: Vivian/campanhas). Add **vertical OOH** (item "Planejamento OOH").
- Rota `/ooh/*` protegida pelo `ProtectedLayout`.

## Decisão A — nome/lugar do repo
- **Promover `uai-spark` → `uai-portal`** (decidido 2026-06-05): mover `~/IdeaProjects/uai-spark` → `~/IdeaProjects/personal/uai/uai-portal` (o nome designado do front oficial; alinha com o `uai-portal/CLAUDE.md`).

## Critério de pronto
- Shell roda com a nav mostrando o **vertical OOH**; rota `/ooh` protegida; UI kit/tema OK.

## Produz
- docs/epicos/runs/EP3-01-shell-nav.md
