# EP3-02 — plataforma: login SSO (uai-auth) — substitui o mock

> Bloco 1 (uAI-OOH F1) · **Épico EP3** (plataforma) · card 2 do kanban.
> **Depende de:** EP3-01 (shell) + **uai-auth** (SSO/introspection pronto) · **Casa com:** EP2-07 (intel valida o token por introspection) · **Paralelizável:** parcial
> **⚠️ GATE:** depende do `uai-auth` ter SSO + introspection funcionando — **outro repo**; confirmar estado.
> **Repo-alvo:** `uai-spark` (→ uai-portal) · **Stack:** React 18 · TS
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §5.1/§7.

## Objetivo
Trocar o **`AuthContext` mock** (hoje só `localStorage` fake) por **auth real via `uai-auth`**: obter token, propagar o **Bearer** nas chamadas ao intel (que valida por introspection — EP2-07).

## Reusa do spark
- `LoginPage` (a casca) · `ProtectedLayout` (o guard de rota já existe).

## Cria (o mock sai)
- **`AuthContext` real**: token + usuário + logout (sai o `localStorage.getItem("uai_auth")`).
- Integração `uai-auth` (fluxo de login → token).
- Propagação do **Bearer** nas chamadas ao intel (interceptor no client de API — o client nasce no EP4).

## Decisão B — como o login funciona
- **SSO redirect pro `uai-auth`** (decidido 2026-06-05): botão "Entrar com uAI" → redireciona → callback com token. Login unificado da plataforma (combina com o mockup aprovado).

## Critério de pronto
- Usuário loga via `uai-auth` (**não** mock); token guardado; rotas protegidas de verdade; **Bearer** propagado pro intel; logout funciona.

## Produz
- docs/epicos/runs/EP3-02-login-sso.md
