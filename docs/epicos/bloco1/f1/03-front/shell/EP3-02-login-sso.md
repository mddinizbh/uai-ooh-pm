# EP3-02 — plataforma: login SSO (uai-auth) — substitui o mock

> Bloco 1 (uAI-OOH F1) · **Épico EP3** (plataforma) · card 2 do kanban.
> **Depende de:** EP3-01 (shell) · **Casa com:** EP2-07 · **Paralelizável:** parcial
> **Gate `uai-auth` RESOLVIDO (2026-06-05):** `uai-auth` é **repo vazio** (bootstrap só no `epic-002-uai-auth-minimo`; JWT RS256 + introspection RFC 7662 planejados, 0 arquivos). → F1 roda em **MODO STUB** (ver abaixo); SSO real quando o `uai-auth` existir. A orquestração mantém esta task em `partial`.
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

## Modo stub (F1 — gate `uai-auth` RED)
`uai-auth` é repo vazio (epic-002). F1 roda em **stub** com a **estrutura real** (troca barata depois):
- **Real já no F1:** `AuthContext` (token+usuário+logout), guard de rota, **interceptor Bearer** nas chamadas ao intel.
- **Stub:** o token vem de um **login local** (não do SSO redirect real).
- **Quando `uai-auth` subir:** trocar login stub → **SSO redirect** + token real — re-rodar `only:['EP3-02']` (orquestração reconcilia `partial` → `done`).

## Critério de pronto
- **F1 (stub):** sai o `localStorage` fake do spark; entra o `AuthContext` real → login local → token guardado → rotas protegidas → **Bearer** propagado pro intel → logout.
- **Final (pós-`uai-auth`):** SSO redirect real + token do `uai-auth`.

## Produz
- docs/epicos/runs/EP3-02-login-sso.md
