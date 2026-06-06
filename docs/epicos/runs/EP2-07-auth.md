# Run — EP2-07 (intel: auth, introspection uai-auth, sem tenant) · 2026-06-06 · 🚧 PARCIAL (auth=stub, fechamento real deferido)

> Execução no `uai-ooh-intel` (branch `feat/ooh-ep2-07`), implementando a proteção de `/api/**` por
> **Bearer token** com `SecurityConfig` (`anyRequest().authenticated()`) + `jsonUnauthorizedEntryPoint()`
> (JSON 401) + filtro Bearer, **autenticação apenas, sem tenant/RLS** (ADR-004). O caminho **real** de
> introspection (`UaiAuthTokenIntrospector`, RFC 7662, **fail-closed**) já está **cabeado**, mas roda em
> **modo STUB** porque o upstream `uai-auth` é **repo vazio (epic-002), gate RED** — a chamada real é
> ligada por flag de config no cutover via OOH. Build **verde** com evidência fresca (JDK 21 forçado,
> Docker up). Estado do banco `ooh` consultado via MCP `postgres-ooh` em 2026-06-06.
> Task do mapa F1: `docs/epicos/bloco1/f1/02-intel-backend/EP2-07-auth.md`.
>
> **Veredito:** o **stub do F1** é o deliverable correto e está completo — compila (Java 21 / Boot 3.3.6),
> **127/127** testes (116 unit + 11 IT com Testcontainers), review **APROVADO**, gate de banco **ok:true**
> e o **refute não derrubou** (`refuted:false`). O que **não fecha como DONE** é o critério **Final**
> (introspection RFC 7662 real contra o `uai-auth`), **deferido** por dependência upstream ausente — exatamente
> o "partial" previsto na task. → **PARCIAL**: stub entregue, **fechamento real deferido** pro cutover.
> Destrava só quando `uai-auth` subir (re-rodar `only:['EP2-07']`).

## Critério de pronto vs. medido (build + banco `ooh`, serving v5 ACTIVE)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| **F1 (stub)** `/api/**` exige Bearer; 401 sem token e com token inválido | 401 nos dois casos | `SecurityConfig` (`anyRequest().authenticated()`) + `jsonUnauthorizedEntryPoint()` (JSON 401); token inválido **interrompe a cadeia**. Coberto por `IntelEndpointsIT` (Testcontainers Postgres) | ✅ |
| **F1 (stub)** `/actuator/health` aberto | health sem auth | `permitAll` no health; `/api/**` + Swagger atrás de auth (Swagger **sob** `/api/**`) | ✅ |
| **F1 (stub)** `SecurityContext` populado, **sem tenant** (ADR-004) | principal autenticado, 0 escopo por org | `SecurityContext` populado com `AuthenticatedUser` + `ROLE_USER`; nenhum `tenant_id`/RLS | ✅ |
| Compila (Java 21 / Spring Boot 3.3.6) | EXIT 0 | `mvn -q -DskipTests compile` = **EXIT 0** (**81** classes em `target/classes`); `mvn -q -DskipTests test-compile` = **EXIT 0** (**21** classes de teste). JDK 21 (`ms-21.0.10`) forçado | ✅ |
| Suíte de testes (estágio Test) | verde | `mvn verify` (JDK 21 forçado): **127 total / 127 pass / 0 fail / 0 skip** (**116** unit + **11** IT `IntelEndpointsIT`); **13 novos** (esperado 13) | ✅ |
| **Gate canônico: validação contra o banco `ooh`** | counts reais confirmam o critério | Gate de banco **`ok:true`** com **`checks:[]`** — auth é comportamento HTTP, **não materializa estado no `ooh`** (ADR-004, sem tabela de auth/token/session). Gate **não falhou**; é **N/A por design**, não confirmação positiva no banco | ✅ (ok:true; N/A por design) |
| **Final (pós-`uai-auth`)** introspection real RFC 7662 + cache curto | 1 call cacheada por hash do token, TTL ~60s, revogação respeitada | `UaiAuthTokenIntrospector` (RFC 7662, **fail-closed**) **cabeado** mas em **modo STUB**: `uai-auth` é **repo vazio (epic-002), gate RED**. Chamada real **deferida** por flag de config (ligada no cutover via OOH) | 🚧 (deferido — não bloqueia o stub) |

## Por que PARCIAL (e não DONE nem FAILED)

Todos os estágios vieram verdes — eles validam o **stub do F1**, que é o **deliverable correto desta task no
F1**. O que **não fecha** como DONE é só o critério **Final** (introspection real RFC 7662 contra o `uai-auth`),
e por um motivo **estrutural e previsto na própria task**, não por defeito da entrega:

- O `uai-auth` é **repo vazio (epic-002), gate RED** — não há endpoint de introspection pra chamar. A task já
  manda manter em `partial` até ele subir. O caminho real (`UaiAuthTokenIntrospector`, RFC 7662, fail-closed)
  está **cabeado** e ligado por flag de config; só falta o upstream existir.
- **Não é FAILED** (diferente do carimbo anterior desta task, em evidência mais velha — 59/59 e gate de banco
  `ok:false`): nesta rodada o **gate de banco devolveu `ok:true`** (com `checks:[]`, por design — auth não conta
  no `ooh`) e o **refute não derrubou** (`refuted:false`). O stub está sólido e aceito.

Resumo: **stub completo e aceito, fechamento real deferido por dependência upstream** ⇒ **PARCIAL**.

## Estado do `dataset_version`

- **Nenhuma alteração — auth não toca dados.** O `ooh` segue com **serving v5 ACTIVE** (a mesma que EP2-03/04/05/06
  consomem read-only). EP2-07 não cria/promove/arquiva versão; é camada de segurança HTTP, ortogonal ao ciclo de
  versões do normalizer (EP1). Coerente com **ADR-052** (fronteira dados↔consumidor). ✅ (inalterado)

## Decisões / desvios

- **Validação = introspection** (decisão 2026-06-05): segue o padrão da plataforma (revogação respeitada,
  +1 call cacheada por hash do token, TTL ~60s). JWT local via JWKS fica como otimização futura. **Não exercida no
  F1** por falta do `uai-auth` — o introspector real está cabeado **fail-closed** e desligado por flag (modo STUB).
- **Sem tenant/RLS (ADR-004).** Intel é dado de referência, tenant-agnóstico — só importa "é usuário uAI
  autenticado". `SecurityContext` carrega `AuthenticatedUser` + `ROLE_USER`; nenhum escopo por organização,
  nenhum `tenant_id` no caminho de auth.
- **Swagger atrás de auth, `/actuator/health` aberto.** Swagger fica **sob `/api/**`** (interno, exige Bearer);
  só o health é `permitAll` (healthcheck de infra).
- **Gancho service-to-service (`/internal/**` + `X-UAI-Internal-Key`) previsto, não implementado** — fora do F1
  (cms→intel no Bloco 3). Caminho deixado, sem código de validação agora.
- **Desvio de harness (aberto desde a EP2-01):** repo **sem wrapper `./mvnw`** — o `mvn` default (3.9.16, Homebrew)
  roda sobre **JDK 26**, enquanto o projeto **alveja `release` 21**; exigiu `JAVA_HOME=<ms-21.0.10>` manual pra
  build/suíte passarem. Não afeta o veredito; pendência **fixar Java 21 + padronizar wrapper** segue carimbada
  pra **EP2-09**.
- **Suíte completa NÃO rodada no estágio implement** (só `compile`/`test-compile` = EXIT 0); os **127/127** são do
  estágio Test, com Docker ativo (Testcontainers no `IntelEndpointsIT`).
- **Código ainda `untracked` na branch `feat/ooh-ep2-07`** (sem commit no momento do run) — normal antes do commit;
  observação registrada no refute, não derruba a entrega.

## Adversarial — o que o cético tentou (resultado: **não derrubou**, `refuted:false`)

O cético tentou derrubar o "done" do **stub** com vetores de build/escopo/segurança. **Nenhum derrubou** — todos
os critérios de pronto (stub) estão cumpridos no `uai-ooh-intel`, com **evidência fresca** rodada agora.

- **Vetor 1 — "o build não está verde / os números não reproduzem".**
  **Não derrubou:** `mvn -o verify` (Java 21, Docker up) reexecutado agora ⇒ **116** testes unitários + **11**
  testes de integração `IntelEndpointsIT` (Testcontainers Postgres) passando, **BUILD SUCCESS** (127/127). Compila
  81 classes + 21 de teste, EXIT 0. Bate com o reportado.
- **Vetor 2 — "o stub não protege de verdade; falta o 401 com token inválido".**
  **Não derrubou:** `/api/**` exige Bearer; **401 sem token** e **401 com token inválido interrompendo a cadeia**,
  ambos cobertos por IT; `/actuator/health` aberto; `SecurityContext` populado com `AuthenticatedUser`+`ROLE_USER`;
  sem tenant/RLS (ADR-004). Review **APROVADO** confirma o critério de pronto do F1-stub.
- **Vetor 3 — "o stub equivale ao comportamento final, então deveria ser DONE".**
  **Não derrubou (e nem tenta):** o STUB é o **deliverable correto** do F1; o caminho real (`UaiAuthTokenIntrospector`,
  RFC 7662, fail-closed) está **cabeado** e **deferido por flag de config** — exatamente o **"partial" esperado**, não
  uma refutação. Equiparar stub a final mascararia o gate `uai-auth` RED; por isso fica **PARCIAL**, não DONE.
- **Observação não-refutadora:** o código está apenas **`untracked`** na branch `feat/ooh-ep2-07` (sem commit ainda)
  — normal antes do commit; não afeta o veredito do run.
- **Gate de banco:** `ok:true` com `checks:[]` — coerente com auth não materializar estado no `ooh` (ADR-004). Não há
  o que contar; o gate **não falhou** (N/A por design), e não foi forçado a confirmar nada que não existe.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`uai-ooh-pm/docs/epicos/runs/EP2-07-auth.md`). **Supersede** o carimbo anterior **FAILED / BLOQUEADA** (evidência
  velha: 59/59 e gate de banco `ok:false`) com evidência fresca **127/127** + gate `ok:true` + `refuted:false`.
- Código no repo-alvo: `uai-ooh-intel` @ `feat/ooh-ep2-07` (`SecurityConfig`, filtro Bearer,
  `jsonUnauthorizedEntryPoint`, `AuthenticatedUser`, `UaiAuthTokenIntrospector` em modo STUB). Compila + 127/127.
- **Status:** 🚧 **PARCIAL** (auth=stub, **fechamento real deferido**) — orquestração mantém em **partial/stub**.
- **Destrava com:** `uai-auth` subir o endpoint de introspection (epic-002) → ligar a chamada real via flag de
  config (cache curto TTL ~60s, fail-closed) → **re-rodar `only:['EP2-07']`** pra promover a DONE.
- **Relaciona:** EP3-02 (login SSO no shell — quem obtém o token); **ITs/deploy** e a pendência **Java 21 + wrapper
  Maven** consolidam em **EP2-09**.
