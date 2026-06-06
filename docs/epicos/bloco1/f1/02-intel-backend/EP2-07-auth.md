# EP2-07 — intel: auth (introspection uai-auth, sem tenant)

> Bloco 1 (uAI-OOH F1) · **Épico EP2** (intel) · card 7 do kanban — **novo**.
> **Depende de:** EP2-01 (scaffold) · **Relaciona:** EP3-02 (login SSO no shell — quem obtém o token) · **Paralelizável:** sim (com EP2-03/04/05/06)
> **Repo-alvo:** `uai-ooh-intel` · **Stack:** Java 21 / Spring Boot (Spring Security)
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §5/§7; `arquitetura-servicos.md` (auth delegada ao `uai-auth` por introspection; ADR-004 sem tenant).

## Objetivo
Proteger a API do intel exigindo **token válido do `uai-auth`**, validado por **introspection**. **Autenticação apenas** — **sem tenant/RLS** (ADR-004): o intel é dado de referência, tenant-agnóstico. Só importa "é um usuário uAI autenticado".

## Como funciona
- O front (logado no shell via SSO `uai-auth`, EP3) manda **Bearer token** nas chamadas ao intel.
- `SecurityFilter` extrai o token → valida via `uai-auth` (**introspection**) → **cache curto** (TTL ~60s, por hash do token) → popula o `SecurityContext`.
- **Protegido:** `/api/**`. **Aberto:** `/actuator/health` (healthcheck infra). **Swagger** atrás de auth (interno).
- Sem tenant: não há escopo por organização.

## Decisão
- **Validação: introspection** (decidido 2026-06-05 — segue o padrão da plataforma; revogação respeitada; +1 call cacheada). JWT local via JWKS fica como otimização futura se a latência incomodar.

## Gancho futuro (fora do F1)
- Service-to-service via `/internal/**` + `X-UAI-Internal-Key` (cms → intel no Bloco 3). Deixar o caminho previsto, sem implementar agora.

## Modo stub (F1 — gate `uai-auth` RED)
`uai-auth` é repo vazio (epic-002) — sem endpoint de introspection. F1 roda em **stub** com a estrutura real:
- **Real já no F1:** `SecurityFilter` + extração do Bearer + paths protegidos (`/api/**` 401 sem token; `/actuator/health` aberto); `SecurityContext` populado.
- **Stub:** a validação aceita um **token de dev/stub** (sem chamar RFC 7662).
- **Quando `uai-auth` subir:** ligar a chamada real de **introspection** (cache curto) — re-rodar `only:['EP2-07']`. A orquestração mantém esta task em `partial` até lá.

## Critério de pronto (verificável)
- **F1 (stub):** `/api/**` exige Bearer (**401** sem token); `/actuator/health` aberto; `SecurityContext` populado; **sem tenant**.
- **Final (pós-`uai-auth`):** validação por **introspection** real (RFC 7662) com cache curto.

## Produz
- docs/epicos/runs/EP2-07-auth.md
