# Run — T8b (Regionalização espacial) · 2026-06-05 · ❌ FAILED / BLOQUEADA

> Execução no `uai-ooh-pipeline` (branch `feat/ooh-t8b`, derivada de `feat/ooh-t8b-regionalizacao`),
> código já mergeado (PR#8 `c97ba05` + PR#9 `0c13005`/T17). Estado **verificado no banco `ooh`** via MCP
> `postgres-ooh` em 2026-06-05.
>
> **Veredito:** os gates de **DADOS** estão verdes e sobreviveram a todo escrutínio, mas a task fecha
> **BLOQUEADA** por **falha de tracking/processo**: a validação nunca foi registrada no run **canônico
> deste hub de PM** (`uai-ooh-pm/docs/epicos/runs/`). O run foi gravado só no repo de código
> (`uai-ooh-pipeline/docs/epicos/runs/T8b-regionalizacao.md`) e hoje está **divergente da realidade do
> banco**. Este arquivo é a correção post-hoc do gap — registrado já com o status real (FAILED) para não
> mascarar que a execução não cumpriu o próprio critério de tracking.

## Counts reais vs. esperado (banco `ooh`, MCP `postgres-ooh`)

| Gate | Esperado | Medido no banco | Veredito |
|---|---|---|---|
| `core.regional` | 9 (as 9 regionais nomeadas) | 9 (Barreiro, Centro-Sul, Leste, Nordeste, Noroeste, Norte, Oeste, Pampulha, Venda Nova) · válidas · SRID 31983 | ✅ |
| `core.census_sector` (total) | 5.166 | 5.166 | ✅ |
| `core.census_sector` c/ `regional` | 0 nulos onde há geom | 5.166/5.166 · 0 sem regional · 0 bairro multi-regional | ✅ |
| `core.stop` `nm_bairro`+`regional` | 0 nulos onde há geom | 9.650/9.650 (249 fora da ∩ resolvidos por KNN) | ✅ |
| `core.line_area` | 303 linhas (≥1 reg. e ≥1 bairro) | 4.039 pares · 303 linhas distintas | ✅ |
| `serving.line_area` (v4 ACTIVE) | regionalização projetada (T17) | 4.039 pares · 303 linhas · 9 regionais · 372 bairros | ✅ |
| `serving.dataset_version` | exatamente 1 ACTIVE | v4 ACTIVE + v3 ARCHIVED = 1 ACTIVE | ✅ |
| **Run canônico em `uai-ooh-pm/docs/epicos/runs/`** | **existir, batendo o banco** | **ausente até este arquivo; run vivia só no `uai-ooh-pipeline`** | ❌ |
| **Coerência do run de código c/ o `dataset_version` vivo** | run reflete o estado atual | **run do pipeline afirma `version_id=1 BUILDING`; v1 nem existe mais no serving** | ⚠️ |

## Decisões / desvios

- **Causa-raiz do bloqueio (gate de tracking `survived:false`):** uma rodada de refute anterior apontou
  "validação não registrada". A tentativa de correção (branch `feat/ooh-t8b`) **não fechou o gate** — o
  agente assumiu que "o PM é proibido tocar" e manteve o run só no `uai-ooh-pipeline`. Leitura **errada**
  da convenção: o `CLAUDE.md` do PM proíbe **código de aplicação**, não apontamentos — e diz explicitamente
  que `docs/epicos/runs/` (os runs) **moram aqui, no hub de PM**, um por task executada em qualquer repo
  OOH. O run de código é cópia; o canônico nunca foi escrito → gate seguiu aberto.
- **Divergência materializada (o risco que a convenção existe pra evitar):** o run no `uai-ooh-pipeline`
  carimba `dataset_version=1 / BUILDING`. No banco **não há v1** no `serving.dataset_version` (só v3
  ARCHIVED e v4 ACTIVE). Logo o run de código é hoje **factualmente obsoleto** sobre a versão — exatamente
  a divergência entre runs duplicados que a regra de "run único no PM" previne.
- **Decisão de fonte (herdada, válida e mantida):** PBH não publica polígono limpo das 9 regionais (BHMap
  só tem `BAIRRO_*`/`DISTRITO`; portal sob WAF GoCache que bloqueia download automatizado). As 9 regionais
  foram derivadas do `nm_subdist` da malha censitária IBGE 2022 (`ST_Union` por subdistrito) — os 9
  subdistritos IBGE de BH = as 9 regionais nome a nome, 0 setores sem subdistrito. Elimina name-matching e
  zera sliver/órfão na ∩ setor→regional. Limite de honestidade carimbado em `COMMENT`: geom = fronteira da
  malha 2022 (**proxy** do limite administrativo, não o traçado jurídico-cartográfico oficial). Sem alteração.
- **Critério de bairro fronteiriço (registrado):** setor→regional por ponto-interior (`ST_PointOnSurface`),
  100% == subdistrito (5.166/5.166). Dos 476 bairros, 18 cruzavam regional → cada bairro recebe **1**
  regional por dominância de área (reatribui 69 setores minoritários ≈1,3%, esperado). Stop→bairro/regional
  por setor mais próximo (KNN `<->`); 249 stops fora da malha herdam do vizinho (dist. máx 1.058 m, p99 255 m).

## Estado do `dataset_version`

- `serving.dataset_version`: **v4 ACTIVE** + **v3 ARCHIVED** (= exatamente 1 ACTIVE). ✅
- **Carimbo do `core` órfão:** `core.census_sector`, `core.stop` e `core.line_area` da T8b estão **100% em
  `version_id=1`** — versão que **não existe** no `serving.dataset_version`. A invariante "core carimbado
  com a `dataset_version` BUILDING corrente" alegada no run de código está, hoje, **violada**: a BUILDING v1
  sumiu no ciclo de versões do Épico 3 (v2→v3→v4).
- **Por que o front F1 ainda é servido certo:** T17 (PR#9) **reprojeta** a regionalização do `core` pro
  `serving.line_area` na versão ACTIVE — e a projeção lê o `core` **independente** do carimbo (o core só tem
  v1 mesmo). Por isso `serving.line_area@v4` está completo (9 regionais / 372 bairros). A T17 **mascara** o
  carimbo órfão; não o corrige.

## Adversarial — o que o cético tentou

- **Tentou derrubar os DADOS e falhou.** Reconferi independente via MCP, sem reimplementar: `core.regional`
  = 9 válidas/SRID 31983; `census_sector` 5.166/5.166 c/ regional e 0 multi-regional; `stop` 9.650/9.650 c/
  bairro+regional; `line_area` 303 linhas/4.039 pares; `serving.line_area@v4` 303 linhas/9 regionais; e
  **exatamente 1 ACTIVE**. Todos os 6 counts do auto-relato batem o banco AGORA, e os checks extras do
  Critério de pronto que o auto-relato não cobria também passam. Gate de dados: **sobreviveu**.
- **Atacou o universo de linhas (304 vs 303) e não derrubou.** `core.line` tem 304 linhas; `line_area` cobre
  303. A única fora é a `320 — "E.E. Amaro Neves"` (`line_id=958553`), com **0 `line_stop`** → rota sem
  itinerário no GTFS, sem área a derivar. Critério ("303 linhas, cada uma ≥1 reg. e ≥1 bairro") satisfeito;
  a 304ª é inelegível por construção, não falha da regionalização.
- **Atacou o TRACKING e derrubou.** O gate "validação registrada no run **correspondente** (canônico, no
  PM)" estava `survived:false` e **continua aberto**: o run morava só no `uai-ooh-pipeline`, e a tentativa
  de correção o deixou lá por uma leitura errada da convenção ("PM proibido"). Resultado concreto: o run de
  código afirma `version_id=1 BUILDING` enquanto o banco vive em **v4 ACTIVE** — divergência real. Aqui o
  cético **ganhou** → task **FAILED/BLOQUEADA**, apesar dos dados verdes.
- **Carimbo de versão órfão (achado secundário, não derruba hoje):** a regionalização do `core` está em v1,
  versão inexistente no `serving.dataset_version`. Não quebra o front porque T17 reprojeta lendo o core sem
  filtro de versão — mas é dívida: num rebuild futuro que gere um `core` em versão nova, o normalizer da T8b
  precisa re-rodar e re-carimbar; o carimbo atual não acompanha o ciclo de `dataset_version`.

## Destrava

1. **Aceitar este arquivo como o run canônico da T8b no PM** (fecha o gap de localização).
2. **Reconciliar o run de código** (`uai-ooh-pipeline/docs/epicos/runs/T8b-regionalizacao.md`): corrigir a
   afirmação de `dataset_version` (não é mais v1 BUILDING) ou rebaixá-lo a ponteiro pro run canônico — para
   não restar dois runs divergentes.
3. **Decidir o destino do carimbo órfão v1** no `core` (re-stamp na próxima BUILDING vs. aceitar a projeção
   T17 como contrato e documentar que `core` da regionalização é version-agnóstico). Bloqueia "done" formal.
4. Só então marcar T8b como concluída no mapa do F1.
