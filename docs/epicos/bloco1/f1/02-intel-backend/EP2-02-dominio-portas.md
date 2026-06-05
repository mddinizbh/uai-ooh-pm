# EP2-02 — intel: domínio + portas (hexagonal)

> Bloco 1 (uAI-OOH F1) · **Épico EP2** (intel) · card 2 do kanban.
> **Depende de:** EP2-01 (scaffold) · **Destrava:** EP2-03 (catálogo+ficha), EP2-04 (ranking), EP2-05 (agregação) · **Paralelizável:** não
> **Repo-alvo:** `uai-ooh-intel` · **Stack:** Java 21 / Spring Boot (domínio sem framework)
> **Origem:** PRD `docs/prd/2026-06-05-ooh-intel-front-f1.md` §6; `epico-4-serving-app.md` (4.3).

## Objetivo
Modelar a camada **hexagonal pura** do intel: records de domínio + tipos finitos + as portas `in`/`out`. **Sem implementação** (isso é EP2-03/04/05) — aqui são contratos que compilam.

## Domínio (`domain/model`, records puros, sem framework)
- `Line(String lineId, String shortName, String longName, BigDecimal scoreTotal)` — lista/catálogo.
- `LineDetail(Line line, String routeType, boolean circular, boolean suplementar, List<Shape> shapes, List<StopRef> stops)` — ficha (shapes carregam GeoJSON).
- `LineMetrics(String lineId, Reach reach, IncomeProfile profile, Demand demand, PoiSummary poi, Arterial arterial, Impressions impressions, ScoreBreakdown score)`.
- `Impressions(BigDecimal util, sab, dom, mes, int faixaIndicativaPct)` — sempre marcado como estimativa na borda.
- `ScoreBreakdown(BigDecimal sAlcance, sDensidade, sPerfil, sArterial, sPoi, scoreTotal)` — os 5 sub-scores.
- `DemographicProfile(String lineId, List<ClasseShare> shares)`; `ClasseShare(ClasseRenda classe, BigDecimal popNaClasse, BigDecimal pctPop)`.
- `ScoreWeights(BigDecimal alcance, densidade, perfil, arterial, poi)` — re-rank.
- `LineRanking(Line line, BigDecimal score, int posicao)`; `AggregateResult(...)` (combinado + per-linha).
- Numéricos como `BigDecimal` (refletem `numeric` do serving).

## Tipos finitos
- `ClasseRenda { A, B, C, D, E }` · `PublicoAlvo { AB, DE }`.
- **Decisão (travada 2026-06-05): `enum`.** São rótulos puros (sem dado por variante) → `enum` + switch expression já é **exaustivo sem `default`** (honra a regra uAI) com menos boilerplate que `sealed`. `sealed` fica reservado p/ tipos finitos cujas variantes **carregam dado/comportamento** (ex.: futuro `ProposalStatus`). Ver `java-enum-rotulos-sealed-variantes`.

## Portas
- **`port/in QueryNetworkUseCase`:** `listLines(LineFilter)` · `lineDetail(String id)` · `lineMetrics(String id)` · `demographicProfile(String id)` · `rankLines(ScoreWeights, PublicoAlvo, LineFilter)` · `aggregate(List<String> ids, PublicoAlvo)` *(assinatura aqui; impl no EP2-05)*.
- **`port/out NetworkQueryRepository`:** `findLines(filter)` · `findLineDetail(id)` · `findMetrics(id)` · `findProfile(id)` · `findForRanking(filter)` · `findForAggregate(ids)` — leem `serving` pela `dataset_version` ACTIVE.
- `LineFilter` cresce no EP2-06 (regiao/bairro/publicoAlvo); aqui entra o esqueleto.

## Convenções
- **IDs como String** (`decisao-ids-gtfs-text-2026-06-05.md`).
- Domínio **sem dependência de framework** (sem Spring/JPA no model).
- **Sem `default`** em switch sobre os tipos finitos (regra uAI).

## Critério de pronto
- Records + tipos finitos + as 2 portas compilam, **sem implementação** (contratos).
- Cobre catálogo, ficha, métricas, perfil, ranking e a assinatura de agregação.
- Domínio sem import de framework; IDs String; sem `default` nos finitos.

## Produz
- docs/epicos/runs/EP2-02-dominio-portas.md
