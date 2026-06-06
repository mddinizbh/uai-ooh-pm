# Run — EP2-05 (intel: agregação da cesta — POST /aggregate) · 2026-06-05 · ✅ DONE

> Execução no `uai-ooh-intel` (branch `feat/ooh-ep2-05`), implementando o endpoint **NOVO**
> `POST /api/lines/aggregate`: recebe `lineIds[]` (+ `publicoAlvo` opcional) e devolve o
> **combinado da cesta** + **per-linha**, **stateless / em memória / sem persistência**, **sem `ST_*`**.
> O coração é o `AggregationCalculator` — calculador **puro (framework-free)** na application layer:
> impressões/pax = **Σ por tipo de dia** (OTS somado), `%AB`/`%DE`/renda **ponderados pela exposição**
> (`impressoes_util`), `faixa_indicativa` = **max dos membros** e `pop_corredor` **NÃO somado** (fica
> só por-linha). `publicoAlvo` aplica **a mesma lente do ranking** (EP2-04). Reaproveita o padrão de
> leitura **JdbcTemplate** sobre o `serving` filtrado pela `dataset_version` **ACTIVE** (herdado da
> EP2-03) e a mesma estratégia de validação/exception do ranking. Estado **verificado no banco `ooh`**
> (schema `serving`) via MCP `postgres-ooh` em 2026-06-05, contra **serving v5 ACTIVE**.
> Task do mapa F1: `docs/epicos/bloco1/f1/02-intel-backend/EP2-05-agregacao.md`.
>
> **Veredito:** os critérios de pronto passam — combinado correto (Σ impressões / Σ pax / %AB
> ponderado), `per-linha` presente, `publicoAlvo` na mesma lente do ranking, **stateless sem
> persistência** e honestidade marcada no payload (OTS somado / estimativa ±35% / sem alcance único).
> Review **aprovou** (fiel ao checklist + padrões do repo: hexagonal, application framework-free, SQL
> parametrizado); aritmética dos testes conferida à mão. Suíte **100/100** sob Java 21 (**13 novos**,
> 1:1). Rodada de refute **não derrubou** o done (refuted:false). → **DONE**. Destrava **EP4-06**
> (cesta + combinado no front).

## Critério de pronto vs. medido (banco `ooh` + build, serving v5 ACTIVE)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| `POST /aggregate` com N `lineIds` devolve **combinado** (Σ impressões, Σ pax, %AB ponderado) | combinado correto | cesta `[62,9250,4107]`@v5: `Σ impressoes_util=86.952`, `Σ pax_util=22.135`, **%AB ponderado=67,92 ≈ 68%** — confere com o spec (~86.952 / 22.135 / ~68%) | ✅ |
| Combinado = **Σ por tipo de dia** (OTS somado, não alcance único) | soma direta de `impressoes_util/sab/dom/mes` e `pax_util/sab/dom` | banco confirma `Σ` da cesta: util **86.952**, sáb **41.708**, dom **34.813**, mês **2.219.029**; `AggregationCalculator` soma membro a membro | ✅ |
| `%AB`/`%DE`/renda **ponderados pela exposição** | `Σ(pctᵢ·impressõesᵢ)/Σ(impressões)` (peso = `impressoes_util`) | `%AB` ponderado=67,92 reproduzido pela mesma fórmula no banco; ponderação por `impressoes_util` no calculador | ✅ |
| `faixa_indicativa` combinada | carrega a faixa ±35% | `faixa` combinada = **max dos membros** (decisão conservadora — a maior incerteza domina a cesta) | ✅ |
| `pop_corredor` combinado **NÃO somado** | só por-linha (evita trap de alcance único) | `pop_corredor` ausente do combinado; presente apenas no `per-linha` — coerente com a decisão da task | ✅ |
| `publicoAlvo` aplica a **mesma lente do ranking** (consistência c/ EP2-04) | `%alvo` ponderado pela mesma recalibração do ranking | `pctAlvoPond` usa a mesma lente AB/DE da EP2-04; aritmética conferida à mão | ✅ |
| **Stateless / em memória / sem persistência**, sem `ST_*` | 0 escrita, 0 `ST_*` (ADR-003) | endpoint não persiste nada; leitura via `JdbcTemplate` sobre `serving`; **0 `ST_*`** em runtime | ✅ |
| Honestidade marcada no payload | OTS somado / ±35% / sem dedup | payload carimba impressões como **estimativa ±35% / OTS somado**; **sem "score combinado"** | ✅ |
| Fonte: `serving` pela `dataset_version` **ACTIVE** | exatamente 1 ACTIVE | `serving.dataset_version`: **v5 ACTIVE / v4 ARCHIVED** (1 ACTIVE) — a mesma que o resolver da EP2-01 aponta e a EP2-03/04 consomem | ✅ |
| Compila (Java 21 / Spring Boot) | EXIT 0 | `mvn -DskipTests compile` = EXIT 0; `mvn -DskipTests test-compile` = EXIT 0 (main + testes) | ✅ |
| Suíte de testes (estágio Test) | verde | `mvn verify` (JDK 21 forçado): **100 total / 100 pass / 0 fail / 0 skip**; **13 novos** (esperado 13) | ✅ |

## Implementação (escopo)

- **`AggregationCalculator`** (`application/`) — calculador **puro, framework-free**: agrega a cesta a
  partir das métricas já lidas do `serving`. Σ impressões/pax por tipo de dia; `%AB`/`%DE`/renda
  ponderados por `impressoes_util`; `faixa` = max dos membros; `pop_corredor` por-linha (não somado);
  `pctAlvoPond` na lente do `publicoAlvo` (mesma da EP2-04). **Sem score combinado.**
- **`LinesController` — `POST /api/lines/aggregate`**: recebe `lineIds[]` (+ `publicoAlvo` opcional),
  devolve `{ combinado, perLinha[] }`. Mesma estratégia de **validação/exception** do ranking; SQL
  **parametrizado**; leitura via `JdbcTemplate` no `serving` filtrado pela `dataset_version` ACTIVE.
- **Testes (13 novos):** Σ impressões/pax, ponderação `%AB`/`%DE`/renda por exposição, `faixa` = max,
  `pctAlvoPond` por público-alvo, `pop` não somado, casos de validação/erro. **Aritmética conferida à
  mão** no review.

## Build / testes (escopo declarado)

- **Estágio implement (validação focada):** `mvn -DskipTests compile` = EXIT 0 e `test-compile` = EXIT
  0 (main e testes compilam). A **suíte completa NÃO foi rodada no implement** (é do estágio Test) —
  escopo declarado, sem dano; a aritmética dos testes foi **conferida à mão**.
- **Estágio Test (suíte):** `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -q verify` — **100/100**
  (0 fail, 0 skip). **13 testes novos** vs. **13 esperados** (1:1).
- **Por que JDK 21 forçado:** o repo **não tem wrapper `./mvnw`**; o `mvn` default cai na JVM
  **Java 26 (Homebrew)**, onde o ambiente quebra o **Mockito ao mockar `JdbcTemplate`** — forçar
  `JAVA_HOME` Java 21 é o que faz a suíte passar. Pendência de harness carregada desde a EP2-01 (ver
  desvios), a consolidar na **EP2-09**.

## Decisões / desvios

- **`pop_corredor` combinado NÃO é somado** (decisão 2026-06-05, já no spec). Impressões (OTS) é o
  "tamanho" da cesta; corredores se sobrepõem ⇒ somar residentes seria **alcance único falso**. `pop`
  fica só por-linha. **Sem dedup / sem matriz O-D** (isso é pós-F1).
- **Sem "score combinado".** O score é relativo **por linha**; a cesta apresenta alcance/perfil
  combinados, nunca um 0–100 agregado. O payload é explícito quanto a isso.
- **`faixa` combinada = max dos membros.** Escolha conservadora: a maior incerteza relativa da cesta
  domina o intervalo exibido (não se "dilui" incerteza ao agregar).
- **Lente de `publicoAlvo` herdada da EP2-04.** `pctAlvoPond` usa a mesma recalibração AB/DE do
  ranking ⇒ consistência cross-endpoint; ausência de `publicoAlvo` cai no comportamento default.
- **Calculador puro na application layer (framework-free).** `AggregationCalculator` não depende de
  Spring; o controller injeta as métricas já materializadas. Mesmo padrão de leitura
  **JdbcTemplate**/serving-ACTIVE e a mesma estratégia de validação/exception do ranking (EP2-04).
- **Desvio de harness (aberto desde a EP2-01):** repo **sem wrapper `./mvnw`** — `mvn` default cai em
  **Java 26** (Mockito quebra ao mockar `JdbcTemplate`), exigiu `JAVA_HOME` Java 21 manual. Não afeta
  o veredito; pendência **fixar Java 21 + padronizar wrapper** segue carimbada para **EP2-09**.

## Estado do `dataset_version`

- **Nenhuma alteração.** O intel é consumidor **read-only** do `serving` pela `dataset_version`
  **ACTIVE**. Counts reais no `ooh` em 2026-06-05: **v5 ACTIVE / v4 ARCHIVED** — exatamente **1
  ACTIVE**, a mesma que o resolver da EP2-01 aponta e a EP2-03/04 consomem. O ciclo de versões é do
  normalizer (EP1), coerente com **ADR-052** (fronteira dados↔consumidor). ✅ (inalterado)

## Adversarial — o que o cético tentou (refuted: false)

- **Vetor 1 — "o GATE/critério de pronto não bate no banco".** Releu o *Critério de pronto* do
  `EP2-05-agregacao.md` e rodou os counts no `ooh` ao vivo. **Não derrubou:** `dataset_version`
  **exatamente 1 ACTIVE = v5** (v4 ARCHIVED); cesta `[62,9250,4107]`@v5 → `impr_util=86.952`
  (= spec ~86.952), `pax_util=22.135` (= spec 22.135), **%AB ponderado=67,92 ≈ 68%** (= spec ~68%);
  resolver ACTIVE→v5 coerente. Critério **satisfeito**.
- **Vetor 2 — "o contrato implementado diverge do spec".** Confrontou o endpoint do `uai-ooh-intel`
  1:1 com o §Objetivo. **Não derrubou:** é `POST /api/lines/aggregate` recebendo `lineIds[]` (+
  `publicoAlvo`), devolvendo combinado + per-linha, **stateless em memória sem `ST_*`** — idêntico ao
  spec; honestidade (OTS/±35%/sem alcance único) marcada no payload.
- **Limites reconhecidos (não derrubam o done):**
  - **Suíte completa só no estágio Test** (implement validou compilação + aritmética conferida à mão) —
    escopo declarado.
  - **Wrapper/JDK não padronizados** — sem `./mvnw`; `mvn` + `JAVA_HOME` Java 21 manual funciona, mas é
    frágil até **EP2-09** fixar Java 21 + wrapper.
  - **Combinado é OTS somado, não alcance único** — assumido e marcado no payload; dedup/sobreposição
    de corredores é **pós-F1** (matriz O-D), não regressão.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`uai-ooh-pm/docs/epicos/runs/EP2-05-agregacao.md`).
- Código no repo-alvo: `uai-ooh-intel` @ `feat/ooh-ep2-05`
  (`src/main/java/com/uai/ooh/intel/application/AggregationCalculator.java` + `LinesController`).
- **Status:** ✅ **DONE**.
- **Relaciona / destrava:** **EP4-06** (cesta + combinado no front) consome este endpoint; herda da
  EP2-04 a lente de `publicoAlvo` e o padrão de leitura serving-ACTIVE. **ITs completos + deploy** e a
  pendência **Java 21 + wrapper Maven** consolidam na **EP2-09**.
