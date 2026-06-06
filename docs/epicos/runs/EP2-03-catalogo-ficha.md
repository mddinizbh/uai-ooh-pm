# Run — EP2-03 (intel: catálogo + ficha, endpoints de leitura) · 2026-06-05 · ✅ DONE

> Execução no `uai-ooh-intel` (branch `feat/ooh-ep2-03`), implementando a **primeira impl das portas**
> da EP2-02: os 3 endpoints de **leitura** (`GET /api/lines`, `/api/lines/{id}`, `/api/lines/{id}/metrics`)
> sobre o `serving` filtrado pela `dataset_version` **ACTIVE**, com **JdbcTemplate** (sem JPA), mappers
> row→record, OpenAPI e **0 `ST_*` no runtime** (ADR-003). O foco operacional desta sessão foi **fechar o
> gate `jacoco:check`** (apontado pelo estágio Test em 65%) **sem tocar produção**. Estado **verificado no
> banco `ooh`** (schema `serving`) via MCP `postgres-ooh` em 2026-06-05, contra serving **v5 ACTIVE**.
> Task do mapa F1: `docs/epicos/bloco1/f1/02-intel-backend/EP2-03-catalogo-ficha.md`.
>
> **Veredito:** os critérios de pronto passam — os 3 endpoints leem o `serving` ACTIVE via SQL puro
> (`serving.* WHERE version_id=?`, `=5`), a lista devolve exatamente **303** linhas com score, 404 via
> `ApiExceptionHandler`→`LineNotFoundException`, OpenAPI publicado (springdoc 2.6.0) e **zero `ST_*`** em
> SQL real. Review aprovou; suite **44/44** sob Java 21; o gate `jacoco:check` (BUNDLE LINE ≥ 0,80) volta
> verde com folga; a rodada de refute **não derrubou** o done. → **DONE**. Destrava EP4-02/03/05 (front).

## Critério de pronto vs. medido (banco `ooh` + build, serving v5 ACTIVE)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| 3 endpoints respondem do `serving` (version ACTIVE), sem `ST_*` | `serving.* WHERE version_id=ACTIVE`; 0 `ST_*` | `LinesController` + adapter/out via `JdbcTemplate`; SQL filtra `version_id=?` (resolver EP2-01 ⇒ ACTIVE=5); **0 `ST_*`** em SQL real (toda ocorrência de `ST_` é comentário/doc ADR-003) | ✅ |
| `GET /api/lines` devolve as **303** linhas com score | 303 | INNER JOIN `line ⋈ line_metrics` ⇒ **303** linhas com `score_total` não-nulo (count real no banco; `metrics_null_score=0`) | ✅ |
| `GET /api/lines/{id}` devolve shapes GeoJSON + pontos | catálogo + `geom_geojson` + `line_stop` | `LineDetail` lê `line` + `line_shape(geom_geojson jsonb)` passando o JSON direto no payload + `line_stop` | ✅ |
| `GET /api/lines/{id}/metrics` devolve `LineMetrics` completo | alcance/perfil/demanda/POIs/arterial/impressões/5 sub-scores | `line_metrics` (+ `line_profile_demografico`) mapeado pro record `LineMetrics` | ✅ |
| **404** p/ id inexistente | tradução p/ 404 | `Optional` vazio do repo → `LineNotFoundException` → `ApiExceptionHandler` ⇒ 404 (coberto no `NetworkQueryServiceTest`) | ✅ |
| OpenAPI/Swagger publicado | spec exposta | springdoc 2.6.0 + anotações nos 3 endpoints; `/swagger-ui` publicado | ✅ |
| Persistência **JdbcTemplate** (decisão 2026-06-05) | SQL explícito, sem JPA/Hibernate | adapter/out `JdbcNetworkQueryRepository` por `JdbcTemplate`; **0** JPA/Hibernate | ✅ |
| Smoke dos 3 endpoints (ITs completos ⇒ EP2-08) | smoke + gate de cobertura verde | testes unitários/smoke + `jacoco:check` (BUNDLE LINE ≥ 0,80) **verde** | ✅ |

## Gate `jacoco:check` — diagnóstico e correção (foco da sessão)

- **Sintoma (estágio Test):** `jacoco:check` reprovava em **~65% LINE**. **Causa-raiz** (confirmada lendo
  o código): `NetworkQueryService` e `JdbcNetworkQueryRepository` ficavam **sem cobertura** — o
  `IntelEndpointsIT` só exercita `ping`/`health`/`docs`/`internal` (**não** chama `/api/lines*`) e o
  `LinesControllerSmokeTest` é `@WebMvcTest` com `QueryNetworkUseCase` **mockado** (não passa pelo
  service real nem pelo repo Jdbc).
- **Correção (sem tocar produção):** adicionados testes unitários cobrindo as 2 classes descobertas —
  `NetworkQueryServiceTest` (6 testes: `listLines`/`lineDetail`/`lineMetrics`, tradução `Optional`→404 e
  os 3 stubs `UnsupportedOperationException`) + `JdbcNetworkQueryRepositoryTest` (7 testes, mappers/SQL).
- **Resultado de cobertura:** surefire-only já entrega **~99% LINE**; as 2 classes antes descobertas vão a
  **100%** (`NetworkQueryService` 11/11, `JdbcNetworkQueryRepository` 79/79). Gate **BUNDLE LINE ≥ 0,80**
  satisfeito com folga.

## Build / testes (escopo declarado)

- **Suite (estágio Test):** `mvn verify` com **JDK 21 forçado** (`JAVA_HOME=<ms-21.0.10>`) — o `mvn`
  default do sistema cai em Java 26 — e **Docker ativo** p/ Testcontainers. **44/44 passou**
  (0 falhas, 0 skips).
- **Novos testes:** **5 criados** vs. **3 esperados** no critério ("smoke dos 3 endpoints") — cobertura
  mais granular que o mínimo (testes unitários de service + repo, não só smoke). Divergência **a mais**,
  sem dano.
- **Refute (build do zero):** `BUILD SUCCESS`; `JdbcNetworkQueryRepositoryTest` 7/7 e
  `NetworkQueryServiceTest` 6/6 verdes; `jacoco:check` verde.

## Decisões / desvios

- **Escopo cirúrgico no gate.** A task desta sessão foi **corrigir APENAS o `jacoco:check`** apontado pelo
  estágio Test, **sem tocar código de produção** — a impl dos 3 endpoints já existia; o que faltava era
  cobrir as 2 classes que o IT/smoke não tocavam. Review confirmou empiricamente sob Java 21.
- **Persistência = JdbcTemplate (decisão 2026-06-05).** SQL explícito p/ o filtro por `version` ACTIVE e
  o `jsonb` GeoJSON passando direto; sem JPA/Hibernate. Vale como **padrão de leitura** p/ EP2-04/05/06.
- **Sem `ST_*` no runtime (ADR-003).** Geometria já vem precomputada como `geom_geojson` (jsonb) no
  `serving`; o adapter só repassa o JSON. Nenhum `ST_*` em SQL real (refute varreu: só comentários/docs).
- **304 linhas no `serving.line` vs. 303 com score.** A lista (`INNER JOIN line ⋈ line_metrics`) devolve
  **303** — a 1 linha sem shapes/métricas (carimbada no Épico 3) fica naturalmente de fora do catálogo
  com score. Coerente, não é bug.
- **Desvio de harness (carregado desde a EP2-01, ainda aberto):** repo **sem wrapper `./mvnw`** — fallback
  p/ `mvn` exigiu `JAVA_HOME` Java 21 manual (default cai em Java 26). Não afeta o veredito; pendência de
  **fixar Java 21 + padronizar wrapper** segue carimbada pra **EP2-09**.

## Estado do `dataset_version`

- **Nenhuma alteração.** O intel é consumidor **read-only** de `serving` pela `dataset_version` **ACTIVE**.
  Counts reais no banco agora: **v5 ACTIVE** (`created 2026-06-05 22:44Z`) e **v4 ARCHIVED**
  (`20:23Z`) — exatamente **1 ACTIVE**, exatamente a que o resolver da EP2-01 aponta. `lines_v5=304`,
  `lines_with_score=303`, `metrics_null_score=0`. O ciclo de versões é do normalizer (EP1), coerente com
  ADR-052 (fronteira dados↔consumidor). ✅

## Adversarial — o que o cético tentou (refuted: false)

- **Vetor 1 — "o gate não fecha de verdade / ainda há `ST_*` ou contrato torto".** Releu `LinesController`
  + adapter/out e o §Critério de pronto. **Não derrubou:** os 3 endpoints existem, todos com SQL
  `serving.*` filtrado por `version_id=?` (ACTIVE=5), 404 via `ApiExceptionHandler`→`LineNotFoundException`,
  OpenAPI publicado (springdoc 2.6.0 + anotações) e **ZERO `ST_*`** em SQL real (toda ocorrência de `ST_`
  é comentário/doc ADR-003). Gate: **sobreviveu**.
- **Vetor 2 — "contrato não bate a tabela da task / armadilha de score nulo".** Confrontou os 3 endpoints
  1:1 com a tabela de endpoints da task. **Não derrubou:** o endpoint de lista (INNER JOIN
  `line ⋈ line_metrics`) retorna exatamente **303** linhas — coincide com "as 303 linhas com score" — e
  `metrics_null_score=0` **elimina** a armadilha de linha com `score_total` nulo entrando no catálogo.
- **Vetor 3 — "os counts reportados não conferem no banco".** Reexecutou as queries. **Não derrubou:**
  bate **agora** no `ooh` — `dataset_version` **id=5 ACTIVE / id=4 ARCHIVED** (só 1 ACTIVE),
  `lines_with_score=303`, `metrics_null_score=0`. Counts: **confirmados**.
- **Limites reconhecidos (não derrubam o done):**
  - **ITs completos dos 3 endpoints ficam pra EP2-08** — aqui foi smoke + cobertura unitária (gate verde);
    o `IntelEndpointsIT` ainda não chama `/api/lines*`.
  - **Wrapper/JDK não padronizados** — sem `./mvnw`; `mvn` + `JAVA_HOME` Java 21 manual funciona, mas é
    frágil até **EP2-09** fixar Java 21 + wrapper.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`uai-ooh-pm/docs/epicos/runs/EP2-03-catalogo-ficha.md`).
- Código no repo-alvo: `uai-ooh-intel` @ `feat/ooh-ep2-03`.
- Próximo: **EP2-04** (ranking) e **EP2-05** (agregação) — em paralelo, reaproveitando o padrão de leitura
  JdbcTemplate desta task. **ITs completos** dos endpoints ⇒ EP2-08; **smoke real + deploy** e a pendência
  **Java 21 + wrapper Maven** ⇒ EP2-09. Pelo lado do front, esta task **destrava EP4-02** (lista+filtros),
  **EP4-03** (ficha) e **EP4-05** (charts).
