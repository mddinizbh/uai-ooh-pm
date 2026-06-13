# CONS-05 — acumulador ao vivo: impressões + ALCANCE com dedup (HLL) (run, 2026-06-11)

> F2 (Bloco 2) · lane **03-consolidator** · task do contrato Redis que o **RT-01 lê** (consolidador é a única escrita).
> Card: `docs/epicos/bloco2/f2/03-consolidator/CONS-05-acumulador-ao-vivo.md` · techspec: `docs/epicos/bloco2/f2/03-consolidator/techspec.md`.
> Novo no replanejamento 2026-06-10 (F2-#6); **alcance ao vivo** adicionado na mesma data (decisão do dono).
> **Status final: DONE.**

## Cabeçalho do run

| Campo | Valor |
|-------|-------|
| Repo-alvo | `uai-ooh-trip-consolidator` *(repo privado `mddinizbh/uai-ooh-trip-consolidator`)* |
| repoPath | `/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-trip-consolidator` |
| Branch | `feat/ooh-cons-05` |
| Base | lane consolidator (CONS-02 estado por veículo + CONS-04 fechamento/terminus por linha) |
| Stack | Java 21 / Spring Boot 3.3.6 · Redis (Lettuce, HLL nativo: PFMERGE/PFCOUNT) · h3-java · Testcontainers (Redis + Postgis) |
| Depende de | CONS-02 ✅ + **RECAL-00** ✅ (HLLs de audiência por hex no Redis, read-only aqui) + CONS-04 (terminus/`lastStopSequenceByRoute`) |
| Pipeline | implement → review → test → validate → refute |

## Counts reais vs. esperado

| Item | Esperado (card/techspec) | Real | OK |
|------|--------------------------|------|----|
| Impressões parciais (soma) | `impressoesParciais += exposure(hex,tipoDia,faixa) × coef`, cache de `core.exposure_cell` em memória, só em hex NOVO | implementado; acumula na entrada de hex novo, mesma régua da lane 06 | ✅ |
| **Alcance ao vivo com dedup (HLL)** | `PFMERGE live:reach:trip:{v}` + `live:reach:day:{v}` com `hll:hex:{...}` → `PFCOUNT` (~±0,8%), cacheado no hash | PFMERGE trip+day por hex novo; **PFCOUNT cacheado em `alcanceParcial` no HASH** pro RT-01 não pagar por request | ✅ |
| Contrato de chaves Redis | `HASH live:vehicle:{code}` (+ `completudeParcial`, `impressoesParciais`, `hexesVisitados`, `alcanceParcial`, ts · TTL 5min); `SET live:line:{lineId}`; `HLL live:reach:trip` / `live:reach:day` | todas as chaves conforme card; bate exatamente com o contrato que o RT-01 consome | ✅ |
| **Vetor #2 — `completudeParcial` no HASH** | escrever no `live:vehicle` (era omitido por `toHash()`) | `RedisLiveStateStore.toHash()` passou a serializar `completudeParcial` — RT-01 agora lê do HASH | ✅ |
| Régua de completude reusa o terminus do CONS-04 | mesmo `lastStopSequenceByRoute` do LAST_STOP / `DefaultTripCloseDetector` | `LiveCompletenessRuler` e `DefaultTripCloseDetector` chamam o **mesmo método** (confirmado por grep) — uma fonte de verdade do terminus | ✅ |
| Fechamento da viagem (CONS-04) | `trip` zera (exato vai pro `medido`/RECAL); `day` segue até virar o dia | comportamento implementado; `live:reach:trip` zera no fechamento, `day` persiste | ✅ |
| Dedup comprovado | posições repetidas no mesmo hex não inflam `alcanceParcial` | merge só em hex NOVO da viagem → repetição = zero trabalho de HLL, `alcanceParcial` estável | ✅ |
| TTLs das chaves | HASH 5min; `live:reach:day` expira na virada do dia | TTL implementado e correto no código | ⚠️ comportamento OK, **sem IT que asserte via `getExpire`** (lacuna de cobertura, não de pronto) |
| `mvn -DskipTests compile` + `test-compile` (implement) | EXIT=0, sem warnings | EXIT=0, sem erros nem warnings (suíte completa não rodada nesse estágio, por instrução) | ✅ |
| `mvn verify` (Java 21, ms-21.0.10) | BUILD SUCCESS · ITs verdes · JaCoCo check OK | **BUILD SUCCESS** · jacoco check OK | ✅ |
| Total da suíte | — | **82 testes · 82 passed / 0 failed / 0 skipped** (test reportou 82; o refute, em `clean verify` do zero, contabilizou **76 unit + 6 IT = 82** — mesma suíte, mesmo verde) | ✅ |
| Testes novos | 2 | 2 criados (esperado 2) | ✅ |
| Intel lê posição+impressões+alcance sem chamar o consolidador | RT-01 só lê Redis; consolidador é a única escrita | contrato Redis fechado; nada de chamada ao consolidador no caminho de leitura | ✅ |

> Os "verdes" são build limpo na validação, não report reaproveitado: o **implement** parou em `compile`/`test-compile` (EXIT=0) por instrução; **test** e **refute** rodaram `mvn verify` / `mvn clean verify` do zero com `JAVA_HOME=<jdk21>`.

## Decisões / desvios

- **HLL nativo do Redis, não estrutura própria.** Alcance com dedup real (~±0,8%) sai de `PFMERGE` (trip + day, a partir dos `hll:hex:*` pré-computados pelo RECAL-00) + `PFCOUNT`. O `PFCOUNT` é **cacheado em `alcanceParcial` no HASH** para o RT-01 ler alcance ao vivo sem pagar um `PFCOUNT` por request. Decisão de produto (dono, 2026-06-10): mostrar alcance subindo ao vivo é argumento ("entregou mais que o estimado") — e soma de hexes mentiria (HLL deduplica gente repetida entre hexes).
- **Vetor #2 (corrigido):** `RedisLiveStateStore.toHash()` omitia `completudeParcial` — o campo era calculado mas não chegava ao HASH que o RT-01 lê. Passou a ser serializado. Confirmado por grep (a árvore `src/` está untracked no branch — estado de scaffold inicial — então o `git diff` só mostra `README.md`; as edições em arquivos existentes não aparecem no diff-stat mas estão confirmadas via grep).
- **Uma só fonte de terminus.** A régua de completude (`LiveCompletenessRuler`) reusa o **mesmo** `lastStopSequenceByRoute` do LAST_STOP / `DefaultTripCloseDetector` (CONS-04) — sem terminus paralelo divergindo entre "fechar viagem" e "calcular completude ao vivo". Verificado por grep que ambos chamam o mesmo método.
- **Merge de HLL só na entrada de hex NOVO** (mesma régua de impressões): veículo sem hex novo no ciclo → zero trabalho de HLL. É o que garante o dedup (posição repetida no mesmo hex não infla o `alcanceParcial`).
- **Reconciliação ≤5% na 4107 NÃO é deste run.** O critério `|PFCOUNT ao vivo − alcance exato do fechamento| ≤ ~5%` é explicitamente trabalho do **e2e local** (lane 07-local) — exige ambiente com replay e fechamento real. CONS-05 isolado entrega o acumulador + contrato; a reconciliação fica para o e2e.
- **Honestidade (ADR-058):** alcance ao vivo é aproximado (HLL ±0,8%) e parcial (só hexes com ping); impressões ao vivo são estimativa (coef. cegos). O selo "estimativa" e o rótulo no front são responsabilidade do WEB-01 — o consolidador só entrega os números, nunca rotulados como "medido".
- **Pendência ambiental herdada da lane:** repo sem `mvnw`; `mvn` default do Homebrew roda Java 26 e quebra (Mockito inline / `java.version=21`; o refute viu `jacoco report-integration` estourar com "Unsupported class file major version 70"). Validação forçou `JAVA_HOME=<jdk21>` (ms-21.0.10). Item aberto desde CONS-01 — travar Java 21 via wrapper/toolchain segue sem resolução, não bloqueia CONS-05.

## Adversarial (o cético — refute)

O refute **não derrubou**. `refuted: false`. Rodou `mvn clean verify` com Java 21 do zero → **BUILD SUCCESS: 76 unit + 6 IT, 0 failures / 0 errors, jacoco check OK**.

Vetores que o cético tentou e por que não derrubaram:

1. **"Critério de pronto não batido — o código existe e está fiado no fluxo real?"** → Existe, está fiado no fluxo real (consumer → estado → acúmulo → escrita no contrato Redis), compila e a suíte completa passa verde **agora** (não report herdado). **Não derrubou.**
2. **"O contrato Redis diverge do card?"** → Bate exatamente: HASH `live:vehicle` com os campos do card (incluindo `completudeParcial` e `alcanceParcial`), `SET live:line`, HLLs `live:reach:trip`/`live:reach:day`. **Não derrubou.**
3. **"A reconciliação ≤5% na 4107 está cumprida?"** → Não é desta task — é da lane 07-local (e2e). Cobrar reconciliação de ponta a ponta do CONS-05 isolado seria cobrar escopo de outra lane. **Não derrubou (escopo errado).**
4. **Único achado real — cobertura de TTL:** **nenhum IT asserta o TTL via `getExpire`**. Mas o comportamento de TTL **está implementado e correto** no código (HASH 5min, `day` na virada do dia). Isso é **fraqueza de teste (cobertura), não critério de pronto não-cumprido nem contrato divergente** — não promove a task a FAILED.

→ **a task sobreviveu.** Verificação independente (review): veredito **APROVADO** — "CONS-05 cumpre a task em todos os eixos, incluindo o coração da unidade, que é o ALCANCE ao vivo com dedup via HyperLogLog nativo (PFMERGE trip+day, PFCOUNT cacheado no HASH para o RT-01 não pagar por request), e não só as impressões". `validate.ok: true` (sem checks pendentes).

## Estado ao fechar este run

- Acumulador ao vivo (impressões somadas + alcance com dedup HLL) + contrato Redis completo na branch `feat/ooh-cons-05`: compila (Java 21), `mvn verify` verde (**82/82**, 76 unit + 6 IT), jacoco check OK. `completudeParcial` agora chega ao HASH; terminus único compartilhado com CONS-04.
- **Não deployado / não em prod** — worker headless, sobe local e nos ITs; sem efeito no Redis de produção.
- **Lacuna conhecida (não-bloqueante):** falta IT assertando TTL via `getExpire` — cobrir num hardening. Comportamento de TTL já correto.
- **Pendências herdadas da lane:** (1) travar Java 21 via `mvnw`/toolchain (aberta desde CONS-01); (2) `src/` untracked no branch — fechar o tracking ao consolidar a lane.
- **Próximo da fila:** lane **07-local** — e2e local com replay para a reconciliação ≤5% na 4107 (PFCOUNT ao vivo vs alcance exato do fechamento) e contract-check do RT-01 lendo o contrato deste run.
