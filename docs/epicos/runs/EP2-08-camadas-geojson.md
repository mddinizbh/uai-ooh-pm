# Run — EP2-08 (intel: camadas GeoJSON — endpoint `/geo`) · 2026-06-06 · ✅ DONE

> Execução no `uai-ooh-intel` (branch `feat/ooh-ep2-08`), implementando
> `GET /api/lines/{id}/geo` → **FeatureCollection GeoJSON** com **4 camadas** (trajeto + corredor 300m
> + **pontos** + POIs por categoria) da version **ACTIVE**, cabeado em todas as camadas hexagonais
> (`LinesController.lineGeo` → `NetworkQueryService.lineGeo` → `NetworkQueryRepository.findLineGeo` →
> `JdbcNetworkQueryRepository`). **Sem `ST_*`** — geometria lida como `jsonb` GeoJSON precomputado
> (ADR-003), SELECT plano com `::text` passthrough filtrado pela `dataset_version` ACTIVE (padrão de
> leitura herdado da EP2-03/04/05/06). Estado **verificado no banco `ooh`** (schema `serving`) via MCP
> `postgres-ooh` em 2026-06-06, contra **serving v5 ACTIVE**.
> Task do mapa F1: `docs/epicos/bloco1/f1/02-intel-backend/EP2-08-camadas-geojson.md`.
>
> **Veredito: ✅ DONE.** Re-execução **pós-desbloqueio do EP1-03** — a 4ª camada (pontos) que faltava
> agora está materializada (`serving.line_stop.geom_geojson` = **84.992 rows / 0 nulls** na v5). O
> endpoint passou a emitir as **4 camadas na ordem `trajeto → corredor → pontos → poi`**. Pipeline
> inteiro verde: compila (`mvn -DskipTests test-compile` EXIT 0, main + test), **review aprovou**,
> suíte **127/127** sob Java 21 (**13 novos**, 1:1), **gate de banco `db.ok=true`** e **refute
> `refuted:false`** (cético atacou 3 vetores contra código + `ooh` + testes e não derrubou). Esta run
> **substitui** o estado FAILED/BLOQUEADA capturado mais cedo em 2026-06-06 (gate DB #2 reprovava
> porque a camada de pontos não existia — gap que era do EP1, não do intel).

## Critério de pronto vs. medido (banco `ooh` + build, serving v5 ACTIVE)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| Endpoint cabeado nas camadas hexagonais | `Controller → Service → Repository → Jdbc` | `LinesController.lineGeo` → `NetworkQueryService.lineGeo` → `NetworkQueryRepository.findLineGeo` → `JdbcNetworkQueryRepository`; review confirmou 1:1 | ✅ |
| `GET /api/lines/{id}/geo` devolve **FeatureCollection** com **4 camadas** | trajeto + corredor 300m + **pontos** + POIs por categoria, da version ACTIVE | linha real **561787** retorna as 4 camadas com GeoJSON válido: **LineString / Polygon / Point / Point** | ✅ |
| **Camada de PONTOS** adicionada (re-impl pós-EP1-03) | lê `serving.line_stop.geom_geojson`, SELECT plano | novo `FIND_STOP_POINTS_SQL` + `STOP_FEATURE_MAPPER`, `::text` passthrough, escopado à version ACTIVE; emite `record StopFeature` (framework-free) | ✅ |
| **Sem `ST_*`** (ADR-003) | 0 `ST_*` no runtime, geom = `jsonb` GeoJSON | só `geom_geojson::text`; **0 `ST_*`** em todas as 4 camadas | ✅ |
| Ordem das camadas estável | `trajeto → corredor → pontos → poi` | `LineGeoResponse.from` emite exatamente nessa ordem | ✅ |
| Não-regressão da ficha | `GET /api/lines/{id}` JSON inalterado | `StopRef` da ficha **intacto**; nova camada não vaza pro contrato existente | ✅ |
| Compila (Java 21 / Spring Boot) | EXIT 0 | `mvn -DskipTests test-compile` = **EXIT 0** (main + test); artefatos `StopFeature.class`, `LineGeo.class`, `JdbcNetworkQueryRepositoryTest.class` | ✅ |
| Review (estágio Review) | aprovado, em escopo | **Aprovado** — 4 camadas, `STOP_FEATURE_MAPPER`, ADR-003 ok, ficha intacta | ✅ |
| Suíte de testes (estágio Test) | verde | `JAVA_HOME=<jdk21> mvn verify`: **127/127** (0 fail, 0 skip); **13 novos** (esperado 13) | ✅ |
| Gate DB #1 — `dataset_version` = **exatamente 1 ACTIVE** | 1 ACTIVE | **v5 ACTIVE + v4 ARCHIVED** (= 1 ACTIVE); resolver lê `version 5 = ACTIVE` | ✅ |
| **Gate DB #2 — camada de pontos materializada (v5)** | `serving.line_stop.geom_geojson` populado, 0 nulls | **84.992 rows / 0 nulls** na v5 (EP1-03 aplicado) | ✅ |
| **Refute (estágio adversarial)** | `refuted:false` (cético tenta e não derruba) | **`refuted:false`** — 3 vetores verificados contra código + `ooh` + testes, todos batem | ✅ |

## Implementação (escopo)

- **4ª camada — PONTOS.** Núcleo desta run: ler `serving.line_stop.geom_geojson` (materializada pelo
  **EP1-03**) e emitir como camada de pontos do `/geo`. Novo `FIND_STOP_POINTS_SQL` +
  `STOP_FEATURE_MAPPER` em `JdbcNetworkQueryRepository` — **SELECT plano**, `geom_geojson::text`
  passthrough, **escopado à version ACTIVE**, **zero `ST_*`** (ADR-003). Novo `record StopFeature`
  (framework-free), no mesmo padrão das demais features.
- **FeatureCollection com 4 camadas** — `LineGeoResponse.from` agora monta a ordem
  **`trajeto → corredor → pontos → poi`** (LineString / Polygon / Point / Point), todas da
  `dataset_version` ACTIVE.
- **`categorias` filtra só POI** — o parâmetro de toggle por categoria continua atuando **apenas** na
  camada de POIs; pontos de parada e trajeto/corredor não são afetados.
- **Não-regressão** — o `StopRef` da ficha (`GET /api/lines/{id}`) ficou **intacto**; a nova camada do
  `/geo` **não muda** o JSON do endpoint de ficha.
- **Cabeamento hexagonal completo** — `LinesController.lineGeo` → `NetworkQueryService.lineGeo` →
  `NetworkQueryRepository.findLineGeo` → `JdbcNetworkQueryRepository`. Review validou cada salto 1:1.

## Build / testes (escopo declarado)

- **Estágio implement:** `mvn -DskipTests test-compile` = **EXIT 0** (main + test sources). `mvnw`
  indisponível no repo → fallback `mvn -q`. Artefatos gerados:
  `target/classes/.../StopFeature.class`, `LineGeo.class` e
  `target/test-classes/.../JdbcNetworkQueryRepositoryTest.class`. Suíte completa **NÃO** rodada aqui
  (é do estágio Test) — escopo declarado.
- **Estágio Test (suíte):** `JAVA_HOME=<jdk21> mvn verify` — **127 total / 127 pass / 0 fail / 0
  skip**; **13 testes novos** vs. **13 esperados** (1:1). O `mvn -q verify` default **falhou por
  ambiente** (JVM default = **Java 26** → Mockito não mocka `JdbcTemplate`); reexecutado **pinado em
  Java 21** p/ casar com o `pom`.

## Decisões / desvios

- **Promove.** `db.ok=true` + `refute=refuted:false` ⇒ critério de pronto fechado contra o `ooh`
  vivo. EP2-08 vai a **DONE**, destravando **EP4-04** (marcadores de parada no mapa do front).
- **Esta run substitui a FAILED/BLOQUEADA de 2026-06-06.** Naquela, o gate DB #2 reprovava porque a
  coluna `serving.line_stop.geom_geojson` **não existia** (a camada de pontos não estava
  materializada) — bloqueio **upstream**, da lane de **dados (EP1-03)**, não do código do intel.
  Resolvido o EP1-03 (84.992 rows / 0 nulls na v5), a re-impl da 4ª camada fechou o gate.
- **Padrão de leitura mantido.** Mesma fórmula das EP2-03/04/05/06: `JdbcTemplate`, SELECT plano por
  `version_id` ACTIVE, geom como `jsonb`/`::text`, **sem PostGIS / sem JPA / sem `ST_*`** (ADR-003).
- **Desvio de harness (aberto desde a EP2-01):** repo **sem `./mvnw`**; `mvn` default cai em Java 26
  (Mockito/`JdbcTemplate` quebram), exigiu `JAVA_HOME` Java 21 manual. Não afetou o resultado, mas
  segue carimbado p/ **EP2-09** (testes + deploy).

## Estado do `dataset_version`

- **Nenhuma alteração por esta task.** Intel é consumidor **read-only** do `serving` pela
  `dataset_version` **ACTIVE**. Counts reais no `ooh` em 2026-06-06: **v5 ACTIVE (label `serving-f1`)
  / v4 ARCHIVED** — exatamente **1 ACTIVE** (gate #1 ✅). A diferença vs. a run anterior é **upstream**:
  `serving.line_stop.geom_geojson` v5 agora está **populada e conforme** (84.992 rows / 0 nulls), fruto
  do **EP1-03**. Ciclo de versões segue do normalizer (EP1), coerente com **ADR-052** (fronteira
  dados↔consumidor).

## Adversarial — o cético (refute) tentou e **NÃO derrubou** (`refuted:false`)

O refute atacou **3 vetores** contra código + banco `ooh` + testes; todos bateram:

- **Vetor 1 — "o endpoint não existe / não lê a ACTIVE / a camada de pontos é vaporware".**
  Verificado: `GET /api/lines/{id}/geo` existe e está cabeado
  (`LinesController` → `NetworkQueryService.lineGeo` → `JdbcNetworkQueryRepository.findLineGeo`); o
  resolver lê `serving.dataset_version` onde **version 5 = ACTIVE**. A **linha real 561787** retorna as
  **4 camadas** com GeoJSON válido (**LineString / Polygon / Point / Point**). A camada de pontos está
  **materializada de verdade**: `serving.line_stop.geom_geojson` = **84.992 rows, 0 nulls** na v5
  (EP1-03 ok). **Sem `ST_*`** em runtime — só `geom_geojson::text`. **Não derrubou.**
- **Vetor 2 — "o contrato não bate com o spec".** Verificado: `LineGeoResponse` = `FeatureCollection`
  com `geometry` raw, `properties.layer` e `properties.categoria` — **idêntico ao spec** do PRD §5.3.
  **Não derrubou.**
- **Vetor 3 — "a nova camada regride a ficha / fura o filtro de categorias".** Verificado: `StopRef`
  da ficha **intacto** (JSON de `GET /api/lines/{id}` **não muda**); `categorias` filtra **só POI**, sem
  tocar pontos/trajeto/corredor. **Não derrubou.** *(A evidência crua deste vetor veio truncada na
  captura; os fatos subjacentes — ficha inalterada e filtro escopado a POI — vêm confirmados pelo
  review.)*

**Por que o gate funcionou como projetado:** na run anterior, build/review/test estavam verdes mas o
**gate contra o `ooh` real** derrubou o done (a 4ª camada não existia no serving). Aqui é o oposto: o
mesmo gate, agora com o EP1-03 aplicado, **confirma** a camada materializada e o refute **certifica**.
A suíte de ITs (fixtures/Testcontainers) não pegaria a ausência da camada no banco vivo — só o gate
contra o `ooh` pega. **Limite reconhecido:** a evidência do vetor 3 ficou parcialmente truncada; os
demais vetores estão confirmados ponta-a-ponta contra dado vivo.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`uai-ooh-pm/docs/epicos/runs/EP2-08-camadas-geojson.md`); **substitui** o estado FAILED/BLOQUEADA
  registrado mais cedo em 2026-06-06.
- Código no repo-alvo: `uai-ooh-intel` @ `feat/ooh-ep2-08`
  (endpoint `GET /api/lines/{id}/geo`; `FIND_STOP_POINTS_SQL` + `STOP_FEATURE_MAPPER` +
  `record StopFeature`; cadeia `NetworkQueryService.lineGeo` → `NetworkQueryRepository.findLineGeo` →
  `JdbcNetworkQueryRepository`).
- **Status:** ✅ **DONE** (db gate `ok=true`; refute `refuted:false`; 127/127; 13 novos).
- **Desbloqueado por:** **EP1-03** (`serving.line_stop.geom_geojson` materializada — 84.992 rows / 0
  nulls na v5). **Destrava:** **EP4-04** (marcadores de parada no mapa do front).
