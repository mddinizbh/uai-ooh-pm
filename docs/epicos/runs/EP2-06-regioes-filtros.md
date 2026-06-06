# Run — EP2-06 (intel: regiões + filtros geográficos) · 2026-06-05 · ✅ DONE

> Execução no `uai-ooh-intel` (branch `feat/ooh-ep2-06`), servindo a **taxonomia região→bairro**
> (pra cascata do front) e o **filtro geográfico** das linhas por área. Três pontos de contrato:
> `GET /api/regions` devolve as **9 regionais** com seus bairros (folding ordenado a partir de
> `serving.line_area`); `GET /api/lines?regiao=&bairro=` e `GET /api/lines/ranking?…&regiao=&bairro=`
> recortam as linhas que **servem** a área via `findLineIdsByArea` (semântica "serve/atravessa" =
> `SELECT DISTINCT line_id` sobre `serving.line_area`). **Sem `ST_*`** — a classificação espacial já
> foi feita no **T8b**; aqui é SELECT plano filtrado pela `dataset_version` **ACTIVE** (padrão de
> leitura JdbcTemplate herdado da EP2-03/04/05). Novo record de domínio `Region(regiao, bairros[])`
> como unidade da cascata. Estado **verificado no banco `ooh`** (schema `serving`) via MCP
> `postgres-ooh` em 2026-06-05, contra **serving v5 ACTIVE**.
> Task do mapa F1: `docs/epicos/bloco1/f1/02-intel-backend/EP2-06-regioes-filtros.md`.
>
> **Veredito:** os **4 itens do critério de pronto** passam — `/api/regions` devolve as 9 regionais
> com bairros (cascata), `/api/lines` e `/ranking` filtram por `regiao`/`bairro` sobre
> `serving.line_area`, e a **re-normalização BH-relativa** é respeitada (ranqueia sobre as **303**
> linhas e só **depois** filtra os IDs — score/posição não mudam com o filtro). Review **aprovou**
> (limpo, em escopo, fiel ao spec; `GATE` do T8b satisfeito na v5). Suíte **114/114** sob Java 21
> (**8 novos**, 1:1). Rodada de refute **não derrubou** o done (refuted:false). → **DONE**. Destrava
> **EP4-02** (filtros/cascata no front).

## Critério de pronto vs. medido (banco `ooh` + build, serving v5 ACTIVE)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| `GET /api/regions` devolve as **9 regionais** + bairros (cascata) | 9 regionais, todas não-nulas, cada uma com bairros | `serving.line_area` v5: `regionais_distintas=9`, `regionais_nao_nulas=9` (Barreiro, Centro-Sul, Leste, Nordeste, Noroeste, Norte, Oeste, Pampulha, Venda Nova), **372 bairros distintos**; controller faz folding ordenado em `Region(regiao, bairros[])` | ✅ |
| `/api/lines` filtra por `regiao`/`bairro` (linhas que **servem** a área) | recorte por área via `line_area` | `findLineIdsByArea` = `SELECT DISTINCT line_id` sobre `serving.line_area` filtrado por `version_id` ACTIVE; sanity: Centro-Sul → **163 linhas servidas** (de 303); **0 `ST_*`** | ✅ |
| `/api/lines/ranking?…&regiao=&bairro=` filtra **no ranking** | mesmo recorte, aplicado após o rank | ranqueia sobre `LineFilter.all()` (303 linhas) e filtra os IDs **depois** — reusa o `findLineIdsByArea`; idem catálogo | ✅ |
| Re-normalização **BH-relativo** (default) | score 0–100 sobre as 303; filtro **não** muda score/posição | rank em memória sobre as **303** linhas (min-max BH-relativo herdado da EP2-04) e só depois recorta os IDs — score e `posicao` de uma linha **independem** do filtro | ✅ |
| Validado contra `serving.line_area` do EP1 (**T8b aplicado**) | `line_area` materializado na versão ACTIVE | `line_area` v5: **303 linhas distintas**, 9 regionais não-nulas, 372 bairros; por regional confere (ex.: Barreiro 53, Centro-Sul 163, Venda Nova 47) | ✅ |
| Fonte: `serving` pela `dataset_version` **ACTIVE** | exatamente 1 ACTIVE | `serving.dataset_version`: **v5 ACTIVE / v4 ARCHIVED** (1 ACTIVE), label `serving-f1` — a mesma que o resolver da EP2-01 aponta e EP2-03/04/05 consomem | ✅ |
| Base do score íntegra (re-normalização sobre as 303) | 303 linhas, 0 nulls | `serving.line_metrics` v5: **303 linhas**, **0 nulls** em `score_total` | ✅ |
| Compila (Java 21 / Spring Boot) | EXIT 0 | `mvn -DskipTests compile` = BUILD SUCCESS; `mvn -DskipTests test-compile` = BUILD SUCCESS (ms-21.0.10) | ✅ |
| Suíte de testes (estágio Test) | verde | `mvn -B verify` (JDK 21 forçado): **114 total / 114 pass / 0 fail / 0 skip**; **8 novos** (esperado 8) | ✅ |

## Implementação (escopo)

- **`Region` (domínio)** — record `Region(regiao, bairros[])`, unidade da cascata região→bairro
  consumida por `GET /api/regions`.
- **`GET /api/regions`** — lê `serving.line_area` (versão ACTIVE), faz folding ordenado regional→bairros
  e devolve `[{ regiao, bairros[] }]` (9 regionais). Bairro é filho de `regiao` (cascata do front).
- **Filtro geográfico em `/api/lines` e `/api/lines/ranking`** — params opcionais `regiao`/`bairro`;
  `findLineIdsByArea` resolve o conjunto de `line_id` que **servem** a área (`SELECT DISTINCT line_id`
  sobre `serving.line_area`) e o controller recorta a saída **depois** do catálogo/rank. Semântica
  "linha serve/atravessa a região" = ≥1 ponto nela; uma linha aparece em várias regiões.
- **Sem `ST_*`** — classificação espacial precomputada no T8b; aqui é SELECT plano parametrizado pela
  `dataset_version` ACTIVE (mesmo padrão JdbcTemplate da EP2-03/04/05).
- **Testes (8 novos):** folding das 9 regionais com bairros, filtro por `regiao`, filtro por `bairro`,
  cruzamento `regiao`+`bairro`, ranking sob filtro preservando posição BH-relativa, e casos de
  área inexistente/vazia.

## Build / testes (escopo declarado)

- **Estágio implement (validação focada):** `mvn -DskipTests compile` e `mvn -DskipTests test-compile`
  = **BUILD SUCCESS** sob Java 21 (ms-21.0.10). Único aviso é **pré-existente**: `unchecked/unsafe
  operations` no `JdbcNetworkQueryRepositoryTest` (generics de `RowMapper` nos mocks) — não introduzido
  por esta task. A **suíte completa NÃO foi rodada no implement** (é do estágio Test) — escopo
  declarado, sem dano.
- **Estágio Test (suíte):** `JAVA_HOME=…/ms-21.0.10/Contents/Home mvn -B verify` — **114/114**
  (0 fail, 0 skip). **8 testes novos** vs. **8 esperados** (1:1).
- **Por que JDK 21 forçado:** o repo **não tem wrapper `./mvnw`**; o `mvn` default cai na JVM
  **Java 26 (Homebrew)**, onde o ambiente quebra o **Mockito/JdbcTemplate** — forçar `JAVA_HOME`
  Java 21 é o que faz a suíte passar. Pendência de harness carregada desde a EP2-01 (ver desvios),
  a consolidar na **EP2-09**.

## Decisões / desvios

- **Re-normalização BH-relativo (decisão 2026-06-05, no spec).** O score é normalizado entre as **303**
  linhas e o filtro `regiao`/`bairro` recorta a exibição **depois** — o score/posição de uma linha
  **não muda** por causa do filtro (comparável sempre). Herdado da re-normalização da EP2-04; a ordem
  de operações (rank sobre `LineFilter.all()` → filtra IDs) é o que garante isso.
- **Semântica "serve a área" = `SELECT DISTINCT line_id`.** Uma linha que toca a região com ≥1 ponto
  entra no recorte; como cruza várias regiões, aparece em vários filtros. Sem dedup espacial, sem `ST_*`
  (a classificação é do T8b). `bairros_distintos=372` é o universo de bairros tocados, não a divisão
  administrativa completa de BH.
- **Cascata região→bairro servida por `/api/regions`.** O front carrega `bairros` de dentro do payload
  de `/api/regions` ao escolher a `regiao`; não há endpoint separado de bairros.
- **Leitura via JdbcTemplate sobre `serving` ACTIVE (padrão herdado).** `line_area` e `line_metrics`
  lidos por `version_id` ACTIVE; intel é consumidor **read-only**. Sem JPA, sem `ST_*`.
- **Desvio de harness (aberto desde a EP2-01):** repo **sem wrapper `./mvnw`** — `mvn` default cai em
  **Java 26** (Mockito/JdbcTemplate quebram), exigiu `JAVA_HOME` Java 21 manual. Não afeta o veredito;
  pendência **fixar Java 21 + padronizar wrapper** segue carimbada para **EP2-09**.

## Estado do `dataset_version`

- **Nenhuma alteração.** O intel é consumidor **read-only** do `serving` pela `dataset_version`
  **ACTIVE**. Counts reais no `ooh` em 2026-06-05: **v5 ACTIVE / v4 ARCHIVED** (label `serving-f1`) —
  exatamente **1 ACTIVE**, a mesma que o resolver da EP2-01 aponta e a EP2-03/04/05 consomem.
  `serving.line_area` v5 (materializado pelo **T8b**): **303 linhas distintas**, **9 regionais**
  (todas não-nulas), **372 bairros distintos**. O ciclo de versões é do normalizer (EP1), coerente
  com **ADR-052** (fronteira dados↔consumidor). ✅ (inalterado)

## Adversarial — o que o cético tentou (refuted: false)

- **Vetor 1 — GATE: "`serving.line_area`/taxonomia do T8b não estão materializados na versão ACTIVE".**
  Rodou os counts no `ooh` ao vivo. **Não derrubou:** `line_area` v5 tem `regionais_distintas=9`,
  `regionais_nao_nulas=9` (Barreiro…Venda Nova), **303 linhas distintas** e **372 bairros distintos**;
  cada regional tem bairros e linhas (ex.: Centro-Sul 40 bairros/163 linhas, Barreiro 57/53, Venda Nova
  25/47). T8b **aplicado** na ACTIVE. GATE **satisfeito**.
- **Vetor 2 — CONTRATO: "os endpoints implementados divergem do spec da task".** Confrontou
  `LinesController`/`RegionsController` 1:1 com a tabela §Endpoints. **Não derrubou:** `GET /api/regions`
  → `[{ regiao, bairros[] }]` (record `Region`); `/api/lines` e `/api/lines/ranking` aceitam
  `regiao`/`bairro` e filtram via `findLineIdsByArea` sobre `serving.line_area`; **0 `ST_*`**;
  re-normalização BH-relativo implementada (rank sobre `LineFilter.all()`, filtra os IDs depois).
  Idêntico ao spec.
- **Vetor 3 — COUNTS: "os números reportados não reproduzem no banco vivo".** Reexecutou os 3 checks.
  **Não derrubou:** `dataset_version` **v5 única ACTIVE** (v4 ARCHIVED); `line_area` v5 com **9
  regionais não-nulas**; base do score íntegra (`line_metrics` v5: **303 linhas / 0 nulls** em
  `score_total`). Tudo bate ao centavo.
- **Vetor 4 — "o filtro muda o score/posição (quebra o BH-relativo)".** Verificou a ordem de operações.
  **Não derrubou:** o rank roda sobre as **303** linhas e só depois recorta os `line_id` da área — o
  score/posição de uma linha **independe** do filtro; coberto por teste novo (ranking sob filtro
  preserva posição). BH-relativo **preservado**.
- **Limites reconhecidos (não derrubam o done):**
  - **Suíte completa só no estágio Test** (implement validou compilação; aviso `unchecked` é
    **pré-existente** no `JdbcNetworkQueryRepositoryTest`, não desta task) — escopo declarado.
  - **Wrapper/JDK não padronizados** — sem `./mvnw`; `mvn` + `JAVA_HOME` Java 21 manual funciona, mas é
    frágil até **EP2-09** fixar Java 21 + wrapper.
  - **`bairros_distintos=372` = bairros tocados por linhas, não a divisão administrativa completa de BH**
    — assumido pela semântica "serve a área"; não é regressão.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`uai-ooh-pm/docs/epicos/runs/EP2-06-regioes-filtros.md`).
- Código no repo-alvo: `uai-ooh-intel` @ `feat/ooh-ep2-06`
  (`src/main/java/com/uai/ooh/intel/domain/model/Region.java` + `LinesController`/`RegionsController`).
- **Status:** ✅ **DONE**.
- **Relaciona / destrava:** **EP4-02** (filtros/cascata no front) consome `GET /api/regions` +
  `regiao`/`bairro` em `/api/lines` e `/ranking`; herda da EP2-04 a re-normalização BH-relativa e o
  padrão de leitura serving-ACTIVE. **ITs completos + deploy** e a pendência **Java 21 + wrapper Maven**
  consolidam na **EP2-09**.
