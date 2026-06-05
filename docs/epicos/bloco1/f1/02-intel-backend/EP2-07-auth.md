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

## Critério de pronto (verificável)
- `/api/**` exige token válido (**401** sem token / token inválido); `/actuator/health` aberto.
- Token validado via `uai-auth` introspection, com cache curto.
- Sem tenant/RLS; usuário autenticado no `SecurityContext`.

## Produz
- docs/epicos/runs/EP2-07-auth.md
