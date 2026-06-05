# EP3-03 — plataforma: deploy do shell

> Bloco 1 (uAI-OOH F1) · **Épico EP3** (plataforma) · card 3 do kanban — closer do EP3.
> **Depende de:** EP3-01 + EP3-02 · **Paralelizável:** não · **Repo-alvo:** `uai-spark` (→ uai-portal) + `uai-infra`
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §5.1; princípios de deploy do `arquitetura-servicos.md`.

## Objetivo
Deployar o shell via **GHCR + uai-infra**, reaproveitando o que o `uai-spark` já tem.

## Reusa do spark (pronto)
- `Dockerfile` · `nginx.conf` · `.github/` (workflows base).

## Ajusta / cria
- Workflow GH Actions → **GHCR** (`ghcr.io/mddinizbh/uai-portal`).
- Entrada no **`uai-infra`** compose.
- Domínio: `app.uaiagencia.com.br` (cutover quando for hora — no F1 o front é interno; pode subir em subdomínio/rota interna primeiro).
- Deploy **só por commit → Actions**; nunca tocar o VPS direto.

## Critério de pronto
- Imagem no GHCR; entrada no `uai-infra`; shell sobe no VPS via Actions; deploy reprodutível.

## Produz
- docs/epicos/runs/EP3-03-deploy-shell.md
