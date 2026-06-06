# Run — EP2-02 (intel: domínio + portas, hexagonal) · 2026-06-05 · ✅ DONE

> Execução no `uai-ooh-intel` (branch `feat/ooh-ep2-02`), modelando a **camada hexagonal pura** do
> intel sobre a base scaffold da EP2-01: records de domínio + tipos finitos + as 2 portas (`in`/`out`),
> **sem implementação** (isso é EP2-03/04/05). Esta task **não toca o banco** — são contratos que
> compilam; validação é por build + grep estrutural, não por counts no `ooh` (MCP `postgres-ooh`
> retornou `checks=[]`, esperado). Task do mapa F1: `docs/epicos/bloco1/f1/02-intel-backend/EP2-02-dominio-portas.md`.
>
> **Veredito:** os 3 critérios de pronto passam — records cobrindo catálogo/ficha/métricas/perfil/
> ranking/agregação, tipos finitos como `enum` (honra a decisão travada em 2026-06-05) e as 2 portas
> como `public interface` **sem impl** (`grep "implements"` em `src/main` vazio), domínio sem import de
> framework, IDs `String`, switch sem `default`. Review aprovou; suite 26/26 (7 testes novos); a rodada
> de refute **não derrubou** o done. → **DONE**. Destrava EP2-03 (catálogo+ficha), EP2-04 (ranking),
> EP2-05 (agregação).

## Critério de pronto vs. medido (contratos — build + estrutura, sem DB)

| Artefato de contrato | Esperado | Medido | Veredito |
|---|---|---|---|
| Records de domínio | cobrir catálogo, ficha, métricas, perfil, ranking, agregação | 10 records nomeados — `Line`, `LineDetail`, `LineMetrics`, `Impressions`, `ScoreBreakdown`, `DemographicProfile`, `ClasseShare`, `ScoreWeights`, `LineRanking`, `AggregateResult` — + VOs de apoio (`Reach`/`IncomeProfile`/`Demand`/`PoiSummary`/`Arterial`, `Shape`/`StopRef`, esqueleto `LineFilter`) | ✅ |
| Tipos finitos como `enum` | `ClasseRenda {A,B,C,D,E}` · `PublicoAlvo {AB,DE}` | 2 enums (`ClasseRenda` A–E, `PublicoAlvo` AB/DE); switch exaustivo **sem `default`** | ✅ |
| Portas `in`/`out` | exatamente 2, assinaturas iguais ao spec | `port/in QueryNetworkUseCase` + `port/out NetworkQueryRepository`, ambas `public interface`; 6 métodos cada conferidos 1:1 com o spec | ✅ |
| **Sem implementação** (EP2-03/04/05 adiados) | 0 impl no domínio | `grep "implements"` em `src/main` ⇒ **vazio** (zero impl prematura) | ✅ |
| Domínio sem framework | 0 imports de Spring/JPA no `model` | 0 imports de framework; IDs como `String`, numéricos `BigDecimal` | ✅ |
| Testes de contrato | 7 novos | `DomainContractsTest` **7/7**; suite cheia **26/26** (19 herdados da EP2-01 + 7 novos) | ✅ |
| Build | test-compile + suite OK | `mvn clean test` BUILD SUCCESS (39 fontes main + 7 test recompiladas) | ✅ |

## Build / testes (escopo declarado)

- **Compilação (estágio implement):** `mvn -DskipTests test-compile` (main **+** test) ⇒ EXIT=0.
  Compilou `test-compile` (não só `compile`) de propósito, p/ garantir que o teste de contratos também
  compila. **Suite não rodada** no implement (ficou pro estágio Test).
- **Suite (estágio Test):** `mvn -q verify` com **JDK 21 forçado**
  (`JAVA_HOME=…/ms-21.0.10/…/Home`) — o `mvn` do Homebrew cai em JDK 26. **26/26 passou**
  (0 falhas, 0 skips), incluindo os **7 testes novos** esperados (7 criados = 7 esperados).
- **Refute (build do zero):** `mvn clean test` recompilou 39 main + 7 test, BUILD SUCCESS;
  `DomainContractsTest` 7/7 verde.

## Decisões / desvios

- **Tipos finitos = `enum` (decisão travada 2026-06-05).** `ClasseRenda` e `PublicoAlvo` são rótulos
  puros (sem dado/comportamento por variante) → `enum` + switch expression já é **exaustivo sem
  `default`**, honrando a regra uAI com menos boilerplate que `sealed`. `sealed` fica reservado p/
  tipos finitos cujas variantes carregam dado (ex.: futuro `ProposalStatus`). Ver memória
  `java-enum-rotulos-sealed-variantes` e o §"Tipos finitos" do `EP2-02-dominio-portas.md`.
- **Só contratos — impl adiada.** As 2 portas são `interface` sem nenhuma implementação; o método
  `aggregate(...)` entra como assinatura aqui e ganha corpo na EP2-05. `grep "implements"` em
  `src/main` vazio comprova que nada vazou de impl prematura.
- **Domínio puro.** `model` sem import de Spring/JPA; IDs como `String`
  (`decisao-ids-gtfs-text-2026-06-05.md`); numéricos como `BigDecimal` (refletem `numeric` do serving).
- **Desvio de harness (carregado da EP2-01, ainda aberto):** o repo **não tem wrapper `./mvnw`** no
  scaffold — fallback p/ `mvn` do sistema funcionou, mas exigiu `JAVA_HOME` manual p/ Java 21 (o `mvn`
  default roda JDK 26). Não afeta o veredito; segue a pendência de **fixar Java 21 + padronizar o
  wrapper** carimbada pra **EP2-09**.
- **Limite de evidência:** o payload do implement enumerou os arquivos só em parte (`files_created`
  truncado a partir de `Line.java`), mas review + a suite 26/26 + o `grep`/clean-build do refute
  confirmam que os contratos existem, compilam e estão sem impl.

## Estado do `dataset_version`

- **Nenhuma alteração.** EP2-02 é camada de domínio/contratos — **zero interação com o banco**
  (`db.checks=[]`). O intel segue consumidor **read-only** de `serving` pela `dataset_version`
  **ACTIVE** (v5, `label='serving-f1'`), exatamente como a EP2-01 deixou; o ciclo de versões é do
  normalizer (EP1), coerente com ADR-052 (fronteira dados↔consumidor). ✅

## Adversarial — o que o cético tentou (refuted: false)

- **Vetor 1 — "o done não bate o critério de pronto".** Releu o §"Critério de pronto" do
  `EP2-02-dominio-portas.md` e confrontou item a item com o repo. **Não derrubou:**
  (1) **clean build do zero** — `mvn clean test` recompilou 39 main + 7 test, BUILD SUCCESS,
  `DomainContractsTest` 7/7 verde;
  (2) **cobertura de domínio** — os 10 records (`Line`, `LineDetail`, `LineMetrics`, `Impressions`,
  `ScoreBreakdown`, `DemographicProfile`, `ClasseShare`, `ScoreWeights`, `LineRanking`,
  `AggregateResult`) + VOs de apoio cobrem catálogo/ficha/métricas/perfil/ranking/agregação;
  (3) **tipos finitos** — `ClasseRenda` enum A–E e `PublicoAlvo` enum AB/DE, switch sem `default`;
  (4) **2 portas como contrato** — `QueryNetworkUseCase` (in) e `NetworkQueryRepository` (out), ambas
  `public interface`, com `grep "implements"` em `src/main` **vazio** → zero impl prematura, honra o
  "sem implementação". Gate de contratos: **sobreviveu**.
- **Limites reconhecidos (não derrubam o done):**
  - **Suite cheia não exercitada no implement** — o implement só rodou `test-compile`; os 26/26 (com 7
    novos) correram no estágio Test e no clean-build do refute.
  - **Wrapper/JDK não padronizados** — sem `./mvnw` no repo; `mvn` + `JAVA_HOME` Java 21 manual
    funciona, mas é frágil até fixar Java 21 + wrapper (pendência → EP2-09).
  - **`files_created` truncado** no payload do implement — coberto por review + grep + clean-build.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`uai-ooh-pm/docs/epicos/runs/EP2-02-dominio-portas.md`).
- Código no repo-alvo: `uai-ooh-intel` @ `feat/ooh-ep2-02`.
- Próximo: **EP2-03** (catálogo + ficha) — primeira impl das portas sobre estes contratos; em paralelo
  EP2-04 (ranking) e EP2-05 (agregação). Pendência operacional segue carregada pra **EP2-09**
  (padronizar Java 21 + wrapper Maven).
