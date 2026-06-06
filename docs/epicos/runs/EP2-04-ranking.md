# Run — EP2-04 (intel: ranking — re-rank por pesos + público-alvo) · 2026-06-05 · ✅ DONE

> Execução no `uai-ooh-intel` (branch `feat/ooh-ep2-04`), implementando o endpoint
> `GET /api/lines/ranking?weights=&publicoAlvo=AB|DE`: recalcula o score **em memória** sobre os 5
> sub-scores (`s_alcance`/`s_densidade`/`s_perfil`/`s_arterial`/`s_poi`), com pesos custom
> (default `model_params`, normalizados se vierem parciais), **recalibração do `s_perfil` por
> público-alvo** (AB→`min-max(pct_ab)`, DE→`min-max(pct_de)`) e re-normalização min-max 0–100,
> **sem `ST_*`**. Reaproveita o padrão de leitura **JdbcTemplate** sobre o `serving` filtrado pela
> `dataset_version` **ACTIVE** (herdado da EP2-03). O foco operacional desta sessão foi um **fix
> cirúrgico de retry (#2)** no `RankingCalculator.rank()` — apontado pelo gate de banco — **sem
> tocar contrato nem produção fora do passo de ordenação**. Estado **verificado no banco `ooh`**
> (schema `serving` + `core.model_params`) via MCP `postgres-ooh` em 2026-06-05, contra **serving
> v5 ACTIVE**.
> Task do mapa F1: `docs/epicos/bloco1/f1/02-intel-backend/EP2-04-ranking.md`.
>
> **Veredito:** os critérios de pronto passam — ranking-sem-params reproduz a ordem do score base,
> `weights`/`publicoAlvo` mexem na ordem como esperado, score 0–100 re-normalizado em memória com
> **0 `ST_*`**, pesos default confirmados no `core.model_params` (`0.35/0.15/0.20/0.20/0.10`,
> soma=1,00) e mapeamento peso↔componente validado no banco (n=303, `max_abs_diff=0,0075`, só
> arredondamento). Review aprovou o fix de retry; suite **84/84** sob Java 21; a rodada de refute
> **não derrubou** o done (refuted:false). → **DONE**. Destrava **EP4-02** (lista/ranking no front).

## Critério de pronto vs. medido (banco `ooh` + build, serving v5 ACTIVE)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| `GET /ranking` **sem params** = mesma ordem do score base (sanity) | ordem idêntica ao catálogo (`score_total DESC, short_name`) | após o fix #2, ranking-sem-params e catálogo usam **o mesmo tie-break** (soma ponderada `raw'` em precisão plena DESC, `short_name` como desempate final) ⇒ ordem reproduzida | ✅ |
| `weights` alteram a ordem | reordenar conforme pesos custom (normalizados se soma≠1) | `RankingCalculator` aplica `Σ wᵢ·subscoreᵢ`; pesos parciais normalizados; coberto nos testes de `RankingCalculatorTest` | ✅ |
| `publicoAlvo=AB` sobe alto `%AB`; `=DE` sobe alto `%DE` | `s_perfil'` SUBSTITUI o base (ADR decisão 2026-06-05) | `s_perfil'` = `min-max(pct_ab)` (AB) / `min-max(pct_de)` (DE); ausente = `s_perfil` base; coberto por teste | ✅ |
| Score **0–100 re-normalizado** entre as linhas; `posicao` correta | min-max 0–100, BH-relativo (303 linhas) | re-normalização min-max em memória sobre as 303 linhas; `posicao` desc | ✅ |
| **Em memória, sem `ST_*`** | 0 `ST_*` no runtime (ADR-003) | re-rank 100% em memória; leitura via `JdbcTemplate` sobre `serving`; **0 `ST_*`** em SQL real | ✅ |
| Pesos default = `model_params` (mapeamento peso↔componente) | soma=1,0; mapa correto | `core.model_params`: `peso_alcance 0.35 / peso_densidade 0.15 / peso_perfil 0.20 / peso_arterial 0.20 / peso_poi 0.10` (**soma=1,00**); mapa confirmado no banco: `score_total ≈ 100·Σ wᵢ·subscoreᵢ`, **n=303**, `max_abs_diff=0,0075` (só arredondamento) | ✅ |
| Fonte: `serving` pela `dataset_version` **ACTIVE** | exatamente 1 ACTIVE | `serving.dataset_version`: **v5 ACTIVE / v4 ARCHIVED** (1 ACTIVE); 303 linhas v5, **0 nulls** em `pct_ab`/`pct_de`/`s_perfil` | ✅ |
| Compila (Java 21 / Spring Boot) | EXIT 0 | `mvn -q -DskipTests compile` = EXIT 0 (BUILD SUCCESS); `test-compile` = EXIT 0 | ✅ |
| Suíte de testes (estágio Test) | verde | `mvn verify` (JDK 21 forçado): **84 total / 84 pass / 0 fail / 0 skip**; **18 novos** (esperado 18) | ✅ |

## Fix cirúrgico do retry #2 — diagnóstico e correção (foco da sessão)

- **Sintoma (apontado pelo gate de banco no run anterior):** o critério *"ranking-sem-params = mesma
  ordem do score base"* não fechava de forma estável — o passo 4 do `RankingCalculator.rank()`
  ordenava pela **soma ponderada já arredondada a 2 casas** (o "score 0–100" exibido). Empates
  artificiais surgiam quando duas linhas colidiam no score arredondado, e o desempate divergia do
  tie-break do catálogo (`score_total DESC, short_name`).
- **Causa-raiz (confirmada no código):** arredondar **antes** de ordenar descarta a precisão que
  separa linhas próximas; a ordem final passava a depender da estabilidade do sort, não do valor
  real.
- **Correção (cirúrgica, fora do contrato):** o passo 4 passou a **ordenar pela soma ponderada `raw'`
  em PRECISÃO PLENA (desc)**, com `short_name` apenas como **desempate final** — só **depois** a soma
  é arredondada a 2 casas para exibição. Resultado: catálogo (EP2-03) e ranking-sem-params (EP2-04)
  passam a usar **o mesmo tie-break determinístico**. Nada mais do `rank()` mudou; contrato e demais
  passos intactos.
- **Teste de regressão:** adicionado caso cobrindo empate no score arredondado que **deve** desempatar
  pela soma em precisão plena (não pelo arredondado) — `RankingCalculatorTest` fechou **7/7** local.

## Build / testes (escopo declarado)

- **Estágio implement (validação focada):** `mvn -q -DskipTests compile` = EXIT 0 e `test-compile`
  = EXIT 0; `mvn -Dtest=RankingCalculatorTest test` → **7/7** (0 fail, 0 error, 0 skip). A **suíte
  completa NÃO foi rodada no implement** (é do estágio Test) — escopo declarado, sem dano.
- **Estágio Test (suíte):** `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify` — **84/84**
  (0 fail, 0 skip). **18 testes novos** vs. **18 esperados** (1:1).
- **Por que JDK 21 forçado:** o repo **não tem wrapper `./mvnw`**; o `mvn` default cai na JVM
  **Java 26 (Homebrew)**, onde o ambiente quebra o **Mockito ao mockar `JdbcTemplate`** — forçar
  `JAVA_HOME` Java 21 é o que faz a suíte passar. Pendência de harness (ver desvios).

## Decisões / desvios

- **Tie-break unificado catálogo↔ranking (decisão do fix #2).** Ordenar pela soma `raw'` em precisão
  plena (`short_name` como desempate) — **nunca pelo score arredondado**. Vale como padrão para
  qualquer ranking derivado dos sub-scores (EP2-05/06 herdam).
- **Público-alvo SUBSTITUI o `s_perfil`** (decisão 2026-06-05, já no spec): `s_perfil'` = aderência
  ao público (AB→`pct_ab`, DE→`pct_de`), re-normalizada. Semântica clara: "ranking pra AB" = quem
  tem mais público AB sobe. Ausência de `publicoAlvo` ⇒ `s_perfil` base.
- **Re-normalização BH-relativa.** Min-max 0–100 entre **todas as 303** linhas; o filtro
  `regiao`/`bairro` (EP2-06) recorta a exibição **depois** — o score de uma linha não muda por causa
  do filtro. Coerente com a decisão carimbada para a EP2-06.
- **Persistência = JdbcTemplate (padrão de leitura herdado da EP2-03).** Carrega os 5 sub-scores +
  `pct_ab`/`pct_de` do `serving` (`version_id=ACTIVE`); re-rank 100% em memória; sem JPA, sem `ST_*`.
- **Pesos default lidos de `core.model_params`** (não hardcoded): confirmado no banco
  `0.35/0.15/0.20/0.20/0.10` (soma 1,00). Pesos parciais do query são **normalizados** antes do
  `Σ wᵢ·subscoreᵢ`.
- **Desvio de harness (carregado desde a EP2-01, ainda aberto):** repo **sem wrapper `./mvnw`** —
  `mvn` default cai em **Java 26** (Mockito quebra ao mockar `JdbcTemplate`), exigiu `JAVA_HOME`
  Java 21 manual. Não afeta o veredito; pendência **fixar Java 21 + padronizar wrapper** segue
  carimbada para **EP2-09**.

## Estado do `dataset_version`

- **Nenhuma alteração.** O intel é consumidor **read-only** do `serving` pela `dataset_version`
  **ACTIVE**. Counts reais agora no `ooh`: **v5 ACTIVE / v4 ARCHIVED** — exatamente **1 ACTIVE**, a
  mesma que o resolver da EP2-01 aponta e a EP2-03 consome. `serving.line_metrics` v5: **303 linhas**,
  **0 nulls** em `pct_ab`/`pct_de`/`s_perfil`. O ciclo de versões é do normalizer (EP1), coerente com
  ADR-052 (fronteira dados↔consumidor). ✅ (inalterado)

## Adversarial — o que o cético tentou (refuted: false)

- **Vetor 1 — "os counts/pesos reportados não conferem no banco".** Reexecutou as queries no `ooh` ao
  vivo. **Não derrubou:** `dataset_version` **v5 ACTIVE / v4 ARCHIVED** (1 ACTIVE); pesos default
  `0.35/0.15/0.20/0.20/0.10` **soma=1,00**; mapeamento peso↔componente confirmado pela query
  `score_total ≈ 100·(0.35·s_alcance + 0.15·s_densidade + 0.20·s_perfil + 0.20·s_arterial + 0.10·s_poi)`
  com **n=303** e **`max_abs_diff=0,0075`** (só arredondamento). Bônus: 303 linhas v5, **0 nulls** em
  `pct_ab`/`pct_de`/`s_perfil`. Counts: **confirmados**.
- **Vetor 2 — "o contrato implementado diverge do spec da task".** Confrontou `LinesController` 1:1 com
  o §Objetivo. **Não derrubou:** é `GET /api/lines/ranking` com params `weights`/`publicoAlvo`,
  retornando `List<LineRanking>`, re-rank **em memória sem `ST_*`** — idêntico ao spec.
- **Vetor 3 — "o sanity do ranking-sem-params ainda não bate o catálogo (o bug do retry #1 sobrevive)".**
  Revalidou em runtime o tie-break. **Não derrubou:** após o fix #2, catálogo e ranking-sem-params
  usam **o mesmo desempate** (`score_total`/soma `raw'` DESC, `short_name`); o teste de regressão de
  empate no score arredondado passa; suite **84/84** sob Java 21. O desvio original do banco está
  **resolvido**.
- **Limites reconhecidos (não derrubam o done):**
  - **Suíte completa só no estágio Test** (implement validou focado em `RankingCalculatorTest`) — escopo
    declarado.
  - **Wrapper/JDK não padronizados** — sem `./mvnw`; `mvn` + `JAVA_HOME` Java 21 manual funciona, mas é
    frágil até **EP2-09** fixar Java 21 + wrapper.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`uai-ooh-pm/docs/epicos/runs/EP2-04-ranking.md`).
- Código no repo-alvo: `uai-ooh-intel` @ `feat/ooh-ep2-04`.
- **Status:** ✅ **DONE**.
- **Relaciona / destrava:** **EP4-02** (lista+ranking no front) consome este endpoint; **EP2-05**
  (agregação) e **EP2-06** (regiões/filtros) herdam o tie-break unificado e a re-normalização
  BH-relativa. **ITs completos + deploy** e a pendência **Java 21 + wrapper Maven** consolidam na
  **EP2-09**.
