# Run — EP2-09 (intel: testes (ITs) + deploy) · 2026-06-06 · ✅ DONE

> Execução no `uai-ooh-intel` (branch `feat/ooh-ep2-09`), o **closer do EP2**: cobre o intel com a
> bateria de **ITs** (Testcontainers `postgres:16` **plain, sem PostGIS** — serving flat, `jsonb`
> GeoJSON, ADR-003) e fecha a cobertura de **todos os endpoints F1**, reconciliando o antigo T20a +
> deploy. Estado **verificado no banco `ooh`** (gate de banco) via MCP `postgres-ooh` em 2026-06-06.
> Task do mapa F1: `docs/epicos/bloco1/f1/02-intel-backend/EP2-09-testes-deploy.md`.
>
> **Veredito: ✅ DONE.** Pipeline inteiro verde: implement compila (`mvn -q -DskipTests test-compile`
> EXIT 0, main + test, Java 21.0.10), **review aprovou** (validou a aritmética do re-rank e o contrato
> JSON **à mão**, já que a suíte não roda na lane de review), suíte **143/143** sob Java 21
> (**27 ITs novos**, 1:1; 0 fail / 0 skip / **0 `@Disabled`**), **gate de banco `db.ok=true`** e
> **refute `refuted:false`** (o cético atacou 3 vetores contra código + `ooh` + relatórios de teste e
> não derrubou). Fecha o **EP2** (intel completo). O **deploy** segue a decisão de 2026-06-05 (sobe no
> VPS via GHCR + `uai-infra`, **sem cutover de nginx**) — ver ressalva de escopo em "Decisões / desvios".

## Critério de pronto vs. medido (suíte + gate de banco `ooh`)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| ITs cobrem **todos** os endpoints F1 | `/api/lines` (+`regiao`/`bairro`/401) · `/{id}`(+404) · `/metrics`(+404) · `/{id}/geo` (camadas+404) · `/ranking` (default/weights/público DE/região/400) · POST `/aggregate`(+400) · `/regions` | `NetworkApiIT` (16 `@Test`) + `IntelEndpointsIT` (11 `@Test`); matriz de status **200×16 / 401×6 / 404×4 / 400** (ranking malformado + aggregate vazio) | ✅ |
| ITs novos | 27 | **27** criados (1:1) | ✅ |
| Suíte verde — `mvn verify` (Java 21) | verde | **143 total / 143 pass / 0 fail / 0 skip**; failsafe-summary `completed=27 errors=0 failures=0 skipped=0`; **0 `@Disabled`** | ✅ |
| Re-rank — aritmética bate com `RankingCalculator` | pesos default / `weights=poi:1` / público DE | review recomputou à mão: default `raw'` 0.655/0.545/0.520 → **100.00/18.52/0.00**; `poi:1` 1.355/0.97/0.835 (**9210 no topo**); DE 0.795/0.50/0.365 (**6250 ultrapassa 9210**) | ✅ |
| Seed `serving.*` fixo (Testcontainers, **sem PostGIS**) | `line`,`line_metrics`,`line_shape`,`line_stop`,`line_profile_demografico`,`line_corridor`,`line_poi`,**`line_area`**, `dataset_version` ACTIVE | seed expandido via `db/init-serving-test.sql`, **copiado pra `test-classes`** no build; ITs seedam o próprio `serving` (não dependem de T8b/EP1-02 rodar) | ✅ |
| `/{id}/geo` — FeatureCollection completo | camadas + filtro de categorias + 404 | IT assere FeatureCollection **completo (7 features no seed)** + filtro `categorias` + 404 | ✅ |
| Compila (estágio implement, Java 21) | EXIT 0 | `mvn -q -DskipTests test-compile` = **EXIT 0** (main + test); `NetworkApiIT.class` gerado; `init-serving-test.sql` copiado pra `test-classes`; recompilou verde após ajuste de comentário | ✅ |
| Review (estágio Review) | aprovado, em escopo | **APROVADO** — IT closer `NetworkApiIT` + seed `serving.*` expandido, correto, bem escopado e conforme à EP2-09 | ✅ |
| Gate DB | `db.ok=true` | **`ok=true`** (`checks=[]` — intel é read-only; ITs usam serving efêmero) | ✅ |
| **Refute (estágio adversarial)** | `refuted:false` | **`refuted:false`** — 3 vetores verificados, todos batem | ✅ |
| JaCoCo ≥80% | ≥80% | rodado no estágio Test (`mvn verify`); **% literal não capturado** no JSON — build verde sugere o gate passou se o `check` estiver vinculado ao `verify` | ⚠️ |
| Deploy GHCR + `uai-infra`, **nginx intocado** | imagem GHCR + entrada compose via Actions | conforme **decisão 2026-06-05**; a evidência capturada nesta run foca nos **ITs + build** (ver "Decisões / desvios") | ⚠️ |

## Implementação (escopo)

- **IT closer `NetworkApiIT`** — núcleo desta run: bateria de ITs que exercita o boot completo do intel
  contra **Testcontainers `postgres:16` plain (sem PostGIS)**, seedando um `serving.*` fixo. Junto com
  `IntelEndpointsIT` (11 `@Test`) totaliza **27 ITs** cobrindo a superfície F1 inteira.
- **Seed `serving.*` expandido** (`db/init-serving-test.sql`, copiado pra `test-classes`) — inclui
  `line_area`, `line_corridor` e `line_poi`, de modo que os ITs **seedam o próprio `serving`** e **não
  dependem do T8b/EP1-02 rodar** (o gate do EP1 vale pro dado de produção, não pros testes).
- **Cobertura de contrato** validada pelo review **à mão** (a suíte não roda na lane de review): a
  aritmética do re-rank (default / `weights=poi:1` / público AB/DE) bate com `RankingCalculator`, e o
  JSON de cada endpoint casa com o spec do PRD.
- **Sem PostGIS / sem `ST_*`** no read path (ADR-003) — o serving de teste é flat (`jsonb` GeoJSON),
  coerente com o runtime e com o serving de produção.

## Build / testes (escopo declarado por estágio)

- **Estágio implement:** `mvn -q -DskipTests test-compile` = **EXIT 0** (Java 21.0.10; main + test).
  Artefato gerado: `target/test-classes/.../web/NetworkApiIT.class`; `db/init-serving-test.sql` copiado
  pra `test-classes`. Recompilou verde após um ajuste de comentário. **A suíte completa (`mvn verify` +
  JaCoCo + Testcontainers) NÃO foi rodada aqui de propósito** — é o estágio Test, exige **Docker**, e a
  **NOTA da lane proíbe** rodar a suíte na lane de implement (mesmo padrão da EP2-08).
- **Estágio Test (suíte):** `JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify -DskipITs=false` —
  **143 total / 143 pass / 0 fail / 0 skip**; **27 ITs novos** vs. **27 esperados** (1:1). JDK **pinado
  em Java 21** p/ casar com o `pom` (Testcontainers exige Docker no host).

## Decisões / desvios

- **Promove.** `db.ok=true` + `refute=refuted:false` ⇒ critério de pronto fechado. EP2-09 vai a
  **DONE** e **fecha o EP2** (intel: catálogo/ficha + ranking + agregação + regiões/filtros + auth +
  camadas GeoJSON, todos cobertos por IT). Reconcilia o antigo **T20a**.
- **Deploy — ressalva de escopo (⚠️).** A decisão vale (2026-06-05): intel sobe no VPS interno via
  **GHCR (`ghcr.io/mddinizbh/uai-ooh-intel`) + `uai-infra` compose**, deploy só por **commit → GitHub
  Actions**, **nunca tocar o VPS direto**, e **sem cutover de nginx** (front é pós-F1; alinhado com a
  nota do T18). **Porém:** a evidência capturada nesta run cobre os **ITs + build + gate de banco** —
  **não** há, no material desta run, uma verificação fresca da imagem publicada no GHCR nem da entrada
  no compose do `uai-infra`. Carimbado honestamente: o **closer de testes está fechado e verificado**;
  o passo de **deploy segue o caminho já decidido** e deve ter sua publicação confirmada no run/estado
  do `uai-infra` (Actions verde) antes de considerar o intel "no ar".
- **Desvio de harness (carregado desde a EP2-01, agora operacionalizado):** repo **sem `./mvnw`**; o
  `mvn` default cai em JVM mais nova (Java 26 → Mockito/Testcontainers quebram), exigindo `JAVA_HOME`
  Java 21 manual — exatamente o que o comando de teste fez
  (`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn verify`). Como EP2-09 é o closer, o desvio fica
  **mitigado na prática** (comando pina Java 21), mas a **raiz permanece aberta** (falta `./mvnw` no
  repo) — débito técnico do `uai-ooh-intel`, não bloqueia o done.

## Estado do `dataset_version`

- **Nenhuma alteração por esta task.** Intel é consumidor **read-only** do `serving` pela
  `dataset_version` **ACTIVE**, e os **ITs usam um `serving` efêmero seedado** (Testcontainers), então
  **não tocam nem dependem** do `dataset_version` real — alinhado com a nota do task ("os ITs seedam o
  próprio serving → não dependem do T8b/EP1-02 rodar"). Em produção o estado segue o herdado da EP2-08:
  **v5 ACTIVE (`serving-f1`) / v4 ARCHIVED** (= 1 ACTIVE). Ciclo de versões segue do normalizer (EP1),
  coerente com **ADR-052** (fronteira dados↔consumidor).

## Adversarial — o cético (refute) tentou e **NÃO derrubou** (`refuted:false`)

Não foi possível derrubar o "done" do EP2-09 com evidência concreta: os critérios de pronto
**verificáveis no repo `uai-ooh-intel`** batem com a realidade **AGORA** — artefatos de build datados
de **hoje 08:01**, **nenhum fonte mais novo** que os relatórios de teste. O cético atacou **3 vetores**:

- **Vetor 1 — "os ITs são fachada (skipados / `@Disabled` / não rodaram de verdade)".**
  Verificado: `failsafe-summary` = `completed=27 errors=0 failures=0 skipped=0`; **0 `@Disabled`**;
  `NetworkApiIT` (16 `@Test`) + `IntelEndpointsIT` (11 `@Test`) = **27 exatos**; artefatos de build de
  **hoje 08:01**, sem fonte mais novo que os relatórios. **Não derrubou.**
- **Vetor 2 — "a cobertura tem buraco (algum endpoint F1 sem IT, ou só caminho feliz)".**
  Verificado: cobertura ponta-a-ponta — `/api/lines` (+`regiao`/+`bairro`/+**401**), `/{id}`(+**404**),
  `/metrics`(+**404**), `/{id}/geo` (FeatureCollection completo **7-features** + filtro `categorias` +
  **404**), `/ranking` (default / `weights` / público **DE** / filtro de região / **400** malformado),
  POST `/aggregate`(+**400** vazio), `/regions`; matriz de status **200×16 / 401×6 / 404×4 / 400**.
  **Não derrubou.**
- **Vetor 3 — "a aritmética do re-rank / o contrato JSON é hand-waving".**
  Verificado: o review **recomputou à mão** contra `RankingCalculator` — default `raw'`
  0.655/0.545/0.520 → **100.00/18.52/0.00**; `weights=poi:1` 1.355/0.97/0.835 (**9210 sobe ao topo**);
  público **DE** 0.795/0.50/0.365 (**6250 ultrapassa 9210**). Tudo bate com o código de produção.
  **Não derrubou.**

**Limite reconhecido.** O refute certificou o **lado do repo** — ITs verdes, cobertura completa,
aritmética/contrato corretos, build fresco. Ele **não** cobriu o passo de **deploy** (publicação GHCR /
entrada `uai-infra` / nginx intocado): isso é verificável fora deste repo (Actions + estado do
`uai-infra`), não no relatório capturado aqui. Por isso o done do **closer de testes** está sólido,
e o **deploy** fica carimbado como ⚠️ a confirmar no run/estado do `uai-infra`.

## Tracking

- Run **canônico** gravado aqui no hub de PM (`uai-ooh-pm/docs/epicos/runs/EP2-09-testes-deploy.md`).
- Código no repo-alvo: `uai-ooh-intel` @ `feat/ooh-ep2-09`
  (IT closer `src/test/java/com/uai/ooh/intel/adapter/in/web/NetworkApiIT.java` + `IntelEndpointsIT`;
  seed `db/init-serving-test.sql` expandido com `line_area`/`line_corridor`/`line_poi`).
- **Status:** ✅ **DONE** (suíte 143/143, 27 ITs novos, 0 skip / 0 `@Disabled`; `db.ok=true`;
  refute `refuted:false`). **Closer do EP2** — intel coberto ponta-a-ponta.
- **Pendência de confirmação (não bloqueia o done dos testes):** publicação da imagem no **GHCR** e
  entrada no **`uai-infra`** (Actions verde, **nginx intocado**) a registrar no estado do `uai-infra`.
