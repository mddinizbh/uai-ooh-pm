# Run — EP2-07 (intel: auth, introspection uai-auth, sem tenant) · 2026-06-05 · ❌ FAILED / 🚧 BLOQUEADA

> Execução no `uai-ooh-intel` (branch `feat/ooh-ep2-07`), implementando a proteção de `/api/**` por
> **Bearer token** com `SecurityConfig` + `BearerTokenAuthenticationFilter` + `AuthenticationEntryPoint`
> (JSON 401), **autenticação apenas, sem tenant/RLS** (ADR-004). A impl em **modo stub** (F1) está
> pronta, compila e passa nos testes — mas a task **não pode ser promovida a DONE**: o critério **Final**
> depende de **introspection real (RFC 7662) contra o `uai-auth`**, que é **repo vazio (epic-002), gate
> RED**; e o **gate canônico deste hub (validação contra o banco `ooh`)** retornou **ok:false** — auth é
> **comportamento HTTP**, não materializa nada no `ooh` (0 tabelas de auth/token/session). Estado do banco
> consultado via MCP `postgres-ooh` em 2026-06-05.
> Task do mapa F1: `docs/epicos/bloco1/f1/02-intel-backend/EP2-07-auth.md`.
>
> **Veredito:** o **stub do F1** está bom (compila Java 21/Boot 3.3.6; **59/59** testes; review APROVADO),
> porém o **DoD completo não é satisfazível agora** — falta o upstream `uai-auth` e o gate de banco é
> **N/A** (não há o que contar no `ooh`). Sem evidência aceita pelo gate canônico + bloqueio upstream ⇒
> a task permanece **partial/stub**, registrada como **FAILED / BLOQUEADA**. Destrava só quando `uai-auth`
> subir (re-rodar `only:['EP2-07']`).

## Critério de pronto vs. medido (build + banco `ooh`, serving v5 ACTIVE)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| **F1 (stub)** `/api/**` exige Bearer; 401 sem token e com token inválido | 401 nos dois casos | `SecurityConfig` (`anyRequest().authenticated()`) + `AuthenticationEntryPoint` (JSON 401) + `BearerTokenAuthenticationFilter` (401 + corta a chain em token inválido). Coberto por `IntelEndpointsIT.apiRejectsRequestWithoutToken`/`apiRejectsInvalidToken` + `BearerTokenAuthenticationFilterTest.invalidTokenIsRejectedWith401AndStopsChain` | ✅ (só por teste de integração, não pelo banco) |
| **F1 (stub)** `/actuator/health` aberto | health sem auth | `permitAll` no health; `/api/**` + Swagger atrás de auth | ✅ |
| **F1 (stub)** `SecurityContext` populado, **sem tenant** (ADR-004) | principal autenticado, 0 escopo por org | filtro popula o `SecurityContext`; nenhum `tenant_id`/RLS | ✅ |
| Compila (Java 21 / Spring Boot 3.3.6) | EXIT 0 | `mvn -DskipTests compile` = EXIT 0; `mvn -DskipTests test-compile` = EXIT 0 | ✅ |
| Suíte de testes (estágio Test) | verde | `mvn verify -B` (JDK 21 forçado): **59 total / 59 pass / 0 fail / 0 skip**; **13 novos** (esperado 13) | ✅ |
| **Gate canônico: validação contra o banco `ooh`** | counts reais confirmam o critério | **NÃO verificável no `ooh`**: query por tabelas `%auth%/%token%/%introspect%/%session%/%tenant%/%security%` ⇒ **0 tabelas**. Auth é comportamento HTTP, não materializa linha no banco | ❌ (ok:false — gate N/A) |
| **Final (pós-`uai-auth`)** introspection real RFC 7662 + cache curto | 1 call cacheada por hash do token, TTL ~60s, revogação respeitada | **NÃO implementável**: `uai-auth` é **repo vazio (epic-002)**, sem endpoint de introspection. Roda em **stub** (aceita token de dev), sem chamar RFC 7662 | ❌ (BLOQUEADA upstream) |

## Por que FAILED / BLOQUEADA (e não DONE)

A confusão honesta aqui: **implement/review/test todos vieram verdes**. Eles validam o **stub do F1** — e o
stub está correto. O que **trava** a promoção a DONE são duas coisas, nenhuma resolvível nesta sessão:

1. **Bloqueio upstream (BLOQUEADA).** O critério **Final** da task exige **introspection real (RFC 7662)**
   contra o `uai-auth`. O `uai-auth` é **repo vazio (epic-002), gate RED** — não há endpoint de
   introspection pra chamar. A própria task prevê isso: *"A orquestração mantém esta task em `partial`
   até [o uai-auth subir]."* Logo o DoD completo é **insatisfazível agora**, por dependência externa.

2. **Gate canônico do hub = N/A (FAILED).** A convenção deste repo (`CLAUDE.md`) é que **validação de
   execução é feita contra o banco `ooh` — counts reais vs. esperado**. EP2-07 é **autenticação = puro
   comportamento HTTP**: não cria tabela, não grava linha, não toca `dataset_version`. A varredura por
   tabelas de auth/token/session/tenant no `ooh` retornou **0** (esperado, por design ADR-004). O gate de
   banco devolveu **ok:false**: não há evidência **no banco** que confirme o 401. A única prova é
   **teste de integração contra o intel no ar com stub de introspection** — válida, mas **fora** do gate
   canônico deste hub.

Resumo: **stub sólido, DoD incompleto**. Verde de compilação/testes **não substitui** (a) o upstream
ausente nem (b) o gate de banco. → a task fica **partial/stub** e é carimbada **FAILED / BLOQUEADA**.

## Estado do `dataset_version`

- **Nenhuma alteração — auth não toca dados.** O `ooh` segue com **v5 ACTIVE** (a mesma que a EP2-03
  consome read-only). EP2-07 não cria/promove/arquiva versão; é camada de segurança HTTP, ortogonal ao
  ciclo de versões do normalizer (EP1). Coerente com ADR-052 (fronteira dados↔consumidor). ✅ (inalterado)

## Decisões / desvios

- **Validação = introspection** (decisão 2026-06-05): segue o padrão da plataforma (revogação respeitada,
  +1 call cacheada). JWT local via JWKS fica como otimização futura. **Não exercida no F1** por falta do
  `uai-auth` — F1 roda só a estrutura real (filtro + extração do Bearer + paths) com **token de dev**.
- **Sem tenant/RLS (ADR-004).** Intel é dado de referência, tenant-agnóstico — só importa "é usuário uAI
  autenticado". Nenhum escopo por organização; nenhum `tenant_id` no caminho de auth.
- **Gancho service-to-service (`/internal/**` + `X-UAI-Internal-Key`) previsto, não implementado** —
  fora do F1 (cms→intel no Bloco 3). Caminho deixado, sem código de validação agora.
- **Desvio de harness (carregado desde a EP2-01, ainda aberto):** repo **sem wrapper `./mvnw`** — `mvn`
  default cai em **Java 26** (incompatível com Lombok), exigiu `JAVA_HOME=<ms-21.0.10>` manual. Não afeta o
  veredito desta task; pendência **fixar Java 21 + padronizar wrapper** segue carimbada pra **EP2-09**.
- **Suíte completa NÃO rodada no estágio implement** (só `compile`/`test-compile`); a suíte 59/59 é do
  estágio Test, com Docker ativo (Testcontainers nos ITs de segurança).

## Adversarial — o que o cético tentou (resultado: **derrubou o "done"**, confirmou FAILED)

Aqui o cético tentou o inverso do usual: **resgatar** a task pra DONE. Não conseguiu — o bloqueio se
sustentou nos 3 vetores.

- **Vetor 1 — "implement+review+test estão todos verdes, então é DONE; FAILED é pedantismo".**
  **Derrubou o resgate:** verde valida o **stub**, não o **DoD**. O critério **Final** (introspection RFC
  7662) é literalmente inimplementável sem `uai-auth` (epic-002, repo vazio). A própria task manda manter
  em `partial` até lá. 59/59 no stub **não fecha** um DoD que exige a chamada real. → segue BLOQUEADA.
- **Vetor 2 — "valida o 401 contra o banco `ooh` e fecha o gate canônico".**
  **Derrubou o resgate:** a varredura por tabelas `%auth%/%token%/%introspect%/%session%/%tenant%/%security%`
  no `ooh` deu **0** — auth não materializa estado no banco (ADR-004, design). O gate de banco é
  estruturalmente **N/A** pra esta task; **ok:false** é o resultado correto, não um falso negativo. A prova
  do 401 existe, mas é **teste de integração HTTP** — fora do gate deste hub. → gate canônico **não fecha**.
- **Vetor 3 — "o stub aceitar token de dev já equivale ao comportamento final".**
  **Derrubou o resgate:** stub **não chama** RFC 7662, **não** respeita revogação nem o cache por hash —
  é placeholder. Equivaler stub a final mascararia exatamente o risco de segurança que a introspection
  existe pra cobrir. → não equivale.
- **Nota sobre o refute formal:** a rodada de `refute` retornou **null** (não produziu contraevidência
  própria) — coerente: não há o que refutar no banco quando o gate é N/A. A decisão de FAILED/BLOQUEADA
  vem do **bloqueio upstream + gate de banco N/A**, não de um refute que derrubou counts.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`uai-ooh-pm/docs/epicos/runs/EP2-07-auth.md`).
- Código no repo-alvo: `uai-ooh-intel` @ `feat/ooh-ep2-07` (stub do F1, compila e 59/59).
- **Status:** **FAILED / BLOQUEADA** — orquestração mantém em **partial/stub**.
- **Destrava com:** `uai-auth` subir o endpoint de introspection (epic-002) → ligar a chamada real
  (cache curto TTL ~60s) → **re-rodar `only:['EP2-07']`** pra promover a DONE.
- **Relaciona:** EP3-02 (login SSO no shell — quem obtém o token); **ITs/deploy** consolidam em **EP2-09**
  (que também fecha a pendência **Java 21 + wrapper Maven**).
