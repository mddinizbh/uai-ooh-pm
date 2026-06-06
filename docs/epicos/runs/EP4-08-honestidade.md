# Run — EP4-08 (front: honestidade na UI · cross-cutting) · 2026-06-06 · ✅ DONE

> Execução no `uai-portal` (ex-`uai-spark`, branch `feat/ooh-ep4-08`), entregando o diferencial
> **"honestidade de medição"** na UI — **separar o sólido do estimado em toda tela**, garantindo que
> **nenhum número apareça mais preciso do que a fonte permite**. Esta é a única task **cross-cutting** do EP4
> (passa por cima de lista/ficha/cesta/export, **não paraleliza**) e o **último card do EP4**. Cria, em
> `src/modules/ooh/components/honestidade/`, o **ledger** (`honestidade.ts` — `FAIXA_INDICATIVA_DEFAULT=35`,
> `VIGENCIAS`, `SOLIDO_DESC`/`ESTIMADO_DESC`), os **selos inline** `EstimativaBadge` (`⚠️ ~est ±35% · OTS`) e
> `SolidoBadge` (em `MedicaoBadge.tsx`) e o **modal "metodologia / como ler"** (`MetodologiaModal.tsx`). O
> ponto que define uma task cross-cutting — e onde o review concentrou o crivo — é que os componentes foram
> **efetivamente cabeados nas 4 superfícies** do critério de pronto, não só criados: **Lista**
> (`RankingList.tsx:55` `SolidoBadge label="score"`), **Ficha** (`LineFichaView.tsx:202` `EstimativaBadge` nas
> impressões + box âmbar `ooh-ficha-honestidade` `:218`), **Cesta** (`Cesta.tsx:132` `EstimativaBadge compact`
> na barra · `:173` no drawer · `:236` box + `:248` `MetodologiaModal`) e **Export** (PDF do EP4-07 —
> `proposalDocument.ts:74/:104` tag `~est · OTS` + disclaimer **sempre presente**, agora importando
> `VIGENCIAS` do mesmo ledger). Reforço: o explainer global "como ler" fica acessível na tela inteira
> (`PlanejamentoPage.tsx:58`). Stack React 18 · TS · shadcn/ui (`badge`/`tooltip`/`dialog`). Tarefa **só de
> front** — **não toca o banco `ooh`** (validação de DB vazia por design). Task do card:
> `docs/epicos/bloco1/f1/03-front/modulo-ooh/EP4-08-honestidade-ui.md`.
>
> **Veredito:** os critérios de pronto passam, com a suíte **reproduzida** (não só alegada): `npx vitest run`
> (fallback — `bun` ausente) ⇒ **164/164** (0 falhas, 0 skips), **+12 vs os 152/152 do EP4-07**. Review
> **aprovou** (escopo e repo-alvo corretos; o que valida uma task cross-cutting — o **wiring real** nas 4
> superfícies — está presente, não só os componentes soltos). O refute **não derrubou** o done. → **DONE**.
> **Fecha o EP4** (front do módulo `/ooh`) — era o último card de ação em aberto.

## Critério de pronto vs. medido (working tree `uai-portal` @ `feat/ooh-ep4-08`)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| Impressões **nunca** aparecem **sem o selo de estimativa** | todo número de impressão carrega `EstimativaBadge`/tag `~est` | impressões só existem em **Ficha** (`LineFichaView.tsx:202` `headerExtra={<EstimativaBadge …>}` na seção `ooh-ficha-impressoes`), **Cesta** (`Cesta.tsx:132` barra · `:173` drawer) e **Export** (`proposalDocument.ts:74/:104` `~est · OTS`) — **toda** ocorrência selada; `RankingList`/`LineCharts` **não exibem impressão** | ✅ |
| **Score/ranking marcados como sólido** | selo de "defensável" no número relativo 0–100 | `RankingList.tsx:55` `SolidoBadge label="score"`; `SOLIDO_DESC` = "score e ranking relativo entre linhas (0–100)… o defensável hoje" | ✅ |
| **Consistente nas 4 superfícies** (lista/ficha/cesta/export) | mesma regra sólido×estimado em todas | Lista ✓ `SolidoBadge` · Ficha ✓ `EstimativaBadge` + box âmbar `:218` · Cesta ✓ `EstimativaBadge` (barra+drawer) + box `:236` · Export ✓ tag `~est · OTS` + disclaimer **incondicional** (EP4-07, `:152`) — **um só ledger** (`honestidade.ts`) alimenta selos, modal e PDF | ✅ |
| **Modal de metodologia acessível** | "como ler" alcançável em toda a tela | `MetodologiaModal` montado **global** em `PlanejamentoPage.tsx:58` (acessível na tela inteira) **e** dentro do drawer da cesta (`Cesta.tsx:248`) — `dialog` shadcn, abre o explainer sólido×estimado + vigências | ✅ |
| **Vigências declaradas** | MCO set/2025 · censo 2022 · embarque mai/2024 (+ demais) | `VIGENCIAS` (`honestidade.ts`): Demanda/MCO **set/2025**, Censo IBGE **2022**, Embarque/ponto **mai/2024**, Velocidade **2019–20**, GTFS/RT **mai/2026** — cada uma com `nota` de defasagem; consumida pelo modal e pelo disclaimer do PDF | ✅ |
| **Reuso** (sem dep nova) | shadcn `badge`/`tooltip`/`dialog` + ledger existente | selos/modal sobre componentes shadcn já no portal; ledger é TS puro (PDF importa `VIGENCIAS` **sem** puxar React) — **nenhuma** lib nova | ✅ |

## Build / testes (escopo declarado)

| Gate | Comando | Resultado | Veredito |
|---|---|---|---|
| Suíte cheia | `npx vitest run` (fallback — `bun` ausente) | **164/164** (0 falhas, 0 skips) — **+12** vs **152/152** do EP4-07 | ✅ |
| Testes novos (EP4-08) | `vitest` (recorte: 7 arquivos / 45 testes-alvo) | **passando** — `MedicaoBadge.test.tsx` (selos sólido/estimado, faixa) + `MetodologiaModal.test.tsx` (abre/fecha, sólido×estimado, vigências) + asserts de wiring em `LineFichaView` / `Cesta` / `CestaMap` / `proposalDocument` / `ProposalPort` | ✅ |
| Build | `vite build` (fallback `npm`; `bun` ausente) | **OK** — **2.574 módulos**, **2,85 s**, `dist/` gerado; aviso de chunk >500 kB **pré-existente** (`maplibre/index`, EP4-04), **não** desta task | ✅ |
| DB | MCP `postgres-ooh` | **vazio por design** (`db.ok = true`, sem checks) — tarefa de front | ✅ |

- **Discrepância de contagem (honesta):** a fase de implementação reportou **12 testes novos**; a
  instrumentação do tester contou **10 "novos esperados"**. O delta da suíte é **164 − 152 = 12**, então a
  contagem de **12** é a que bate com o número real; os **10** parecem contar arquivos-de-teste-novos (2:
  `MedicaoBadge`/`MetodologiaModal`) + um recorte, não os asserts adicionados aos testes de wiring já
  existentes. **Não muda o veredito** — 0 falhas em qualquer recorte; registrado por transparência (esta é,
  afinal, a task da *honestidade*).
- **Toolchain:** runner via `npx vitest` e build via `npm`/`vite` (**fallback** — `bun` ausente no ambiente).
  Pendência operacional **viva desde EP3-01/EP4-01/EP4-04/EP4-05/EP4-06/EP4-07**: padronizar o runner
  (instalar `bun` ou fixar `vitest` no CI) p/ não depender de `npx`/`npm` ad-hoc.

## Decisões / desvios

- **Badges inline + modal de metodologia (decisão da task confirmada):** selo em **todo** número estimado +
  um modal "como ler" **acessível ao planejador** (a honestidade é diferencial de venda — dá pra mostrar ao
  cliente). *(Alternativa descartada: só badges inline, sem o explainer navegável.)*
- **Um ledger único (`honestidade.ts`) como fonte da verdade textual:** `FAIXA_INDICATIVA_DEFAULT=35`,
  `VIGENCIAS`, `SOLIDO_DESC`/`ESTIMADO_DESC` ficam num **TS puro, DOM-free**. Selos (`MedicaoBadge`), modal
  (`MetodologiaModal`) **e** o disclaimer do PDF (`proposalDocument`) consomem o **mesmo** texto — o PDF
  importa `VIGENCIAS` **sem** puxar o barrel de componentes React. Evita drift entre "o que a tela diz" e "o
  que a proposta diz".
- **Sólido × estimado = regra de ouro, materializada na arquitetura das telas (não só em copy):** o
  **sólido** (score/ranking relativo 0–100, defensável) aparece em Lista (`SolidoBadge`); o **estimado**
  (impressões absolutas = **OTS**, ±~35%, coeficientes **não calibrados**) só aparece **selado**. Decisão de
  fundo: `RankingList` e `LineCharts` **deliberadamente não exibem impressões absolutas** — a lista ranqueia
  pelo número defensável e os charts plotam demanda/perfil/sub-scores. Assim o "estimado sem selo" é
  **impossível por construção** nessas superfícies, não só por convenção.
- **Cross-cutting ⇒ o entregável é o wiring, não os componentes:** o valor da task não é o `EstimativaBadge`
  existir, e sim ele estar **plugado nos 4 lugares certos** das telas do EP4-02/03/06/07 sem alterar suas
  assinaturas. Os componentes entram como `headerExtra`/badge auxiliar — `LineFichaView`, `RankingList`,
  `Cesta` **mantêm a mesma interface**; só ganharam o selo.
- **Faixa indicativa ≠ margem estatística:** o box da ficha (`:226`) carimba **±N%** explicitando que **não
  é** intervalo de confiança — é incerteza-de-proxy do ledger. Coerente com o disclaimer do PDF (EP4-07).

## Honestidade — o que a UI promete (sólido) vs. carimba como estimativa

- **Sólido (vendável hoje):** **score e ranking relativo entre linhas (0–100)** e a comparação — o defensável.
  Selado com `SolidoBadge` na Lista; é o número pelo qual a lista ordena.
- **Estimado (nunca sem selo):** **impressões absolutas = OTS** (contatos visuais, **não pessoas únicas**;
  sobreposições **não descontadas**), faixa **±35%** default, **coeficientes de exposição não calibrados**
  (ilustrativo até estudo de campo). Aparece só em Ficha/Cesta/Export, sempre com `EstimativaBadge`/tag +
  box/disclaimer.
- **Vigências ("colagem de épocas"):** MCO/embarque **set/2025** (~8 meses defasado) · censo IBGE **2022**
  (renda do responsável) · embarque/ponto **mai/2024** (distribuição, não volume) · velocidade **2019–20**
  (pré-pandemia) · GTFS/RT **mai/2026**. Declaradas no modal e no disclaimer do PDF.

## Contract-check back↔front (review)

- **N/A no sentido de API — task puramente de apresentação.** EP4-08 **não** chama o `uai-ooh-intel` nem
  pede campo novo: lê o que as telas já recebem (`impressions.faixaIndicativaPct` do `/lines`/`/aggregate`,
  EP2-03/EP2-05) e **decora** com selo/box/modal. O único contrato relevante é o **textual interno**: o
  `faixaIndicativaPct` carimbado pelo back é a fonte do "±N%" exibido — o front **não inventa** a faixa,
  cai pro `FAIXA_INDICATIVA_DEFAULT=35` só quando o agregado ainda não trouxe valor.
- **Assinaturas das telas preservadas:** os selos entram como adorno (`headerExtra`/badge) — `RankingList`,
  `LineFichaView`, `Cesta` e o `proposalDocument` **não mudaram de contrato**; EP4-02/03/06/07 seguem intactos.

## Estado do `dataset_version`

- **N/A — tarefa de front.** O `uai-portal` é o shell e **não acessa o banco `ooh`** nesta task; a validação
  de DB veio **vazia por design** (`db.ok = true`, sem checks). A honestidade é **camada de apresentação** —
  carimba a procedência/incerteza dos números que já chegam servidos. Nenhum ciclo de versão é tocado aqui;
  promoção do `dataset_version` segue pendência do EP1/serving, não daqui. Os **valores reais** (impressões,
  %AB, faixa) só preenchem os selos com o `uai-ooh-intel` no ar (EP2-03/EP2-05/EP2-09/deploy) a montante — o
  que esta task garante é que, **sejam quais forem**, sairão com o selo certo.

## Adversarial — o que o cético tentou (refuted: false)

O vetor foi confrontado contra a **working tree atual** do `uai-portal`; **não derrubou** o done.

- **Vetor único (GATE/critério de pronto) — "os critérios não estão (ou estão só parcialmente) cumpridos no
  `uai-portal`": impressões nunca sem selo de estimativa; score/ranking marcados como sólido; consistente nas
  4 superfícies; modal de metodologia acessível + vigências declaradas.** **Não derrubou (refuted: false):**
  - **(a) impressões sempre seladas** — a busca confirma que impressão **só** ocorre em Ficha
    (`LineFichaView.tsx:202` `EstimativaBadge` + box `ooh-ficha-honestidade` `:218`), Cesta (`Cesta.tsx:132`
    barra · `:173` drawer, ambas `EstimativaBadge` + box `:236`) e Export (`proposalDocument.ts:74/:104`
    `~est · OTS` + disclaimer **SEMPRE presente** `:152`). **Nenhuma** impressão escapa.
  - **(b) onde NÃO há selo, não há impressão** — `RankingList.tsx:55` mostra **só score** com `SolidoBadge`,
    sem impressões; `LineCharts` plota demanda/perfil/sub-scores, **sem** impressões. O caminho "estimado sem
    selo" é **fechado por construção**, não por convenção.
  - **(c) sólido marcado** — `SolidoBadge label="score"` na Lista; `SOLIDO_DESC` no ledger.
  - **(d) 4 superfícies + modal + vigências** — um **único ledger** (`honestidade.ts`) alimenta selos, modal e
    PDF; `MetodologiaModal` acessível global (`PlanejamentoPage.tsx:58`) e no drawer (`Cesta.tsx:248`);
    `VIGENCIAS` declara set/2025 · 2022 · mai/2024 (+velocidade 2019–20 +GTFS mai/2026).
  - Reforço: `npx vitest run` **164/164 AGORA** (+12 vs EP4-07), com testes dedicados de selo, modal e wiring.
- **Limite reconhecido (não derruba):** o que está provado é **regra sólido×estimado + wiring nas 4
  superfícies + modal/vigências** por código e teste — **não** uma inspeção visual pixel-a-pixel de cada
  badge renderado em runtime, nem os **valores reais** dos números selados (esses só com o `uai-ooh-intel` no
  ar, EP2-03/EP2-05/EP2-09/deploy). Aqui se prova que **qualquer** número de impressão sai carimbado e que o
  score é o defensável — não um render contra dados servidos ao vivo.
- **Ponto de transparência (não-falha):** discrepância **12 vs 10** na contagem de testes novos — o delta
  real da suíte (164−152=12) confirma os **12**; registrado em "Build / testes" sem impacto no veredito.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/epicos/runs/EP4-08-honestidade.md`).
- Código no repo-alvo: `uai-portal` (ex-`uai-spark`) @ `feat/ooh-ep4-08` — módulo `/ooh`:
  `src/modules/ooh/components/honestidade/` (`honestidade.ts` ledger · `MedicaoBadge.tsx`
  `EstimativaBadge`+`SolidoBadge` · `MetodologiaModal.tsx` · `index.ts` barrel) + wiring em
  `RankingList.tsx` (Lista), `LineFichaView.tsx` (Ficha), `cesta/Cesta.tsx` (barra/drawer/modal),
  `proposal/proposalDocument.ts` (PDF, via `VIGENCIAS`) e `pages/PlanejamentoPage.tsx` (modal global);
  testes novos `MedicaoBadge`/`MetodologiaModal` + asserts de wiring (suíte **164/164**).
- **EP4 fechado.** Próximo: cutover pós-F1 (aposentar o legado `uai-bus-lines-map` quando
  `uai-ooh-intel` + `uai-ooh-web`/portal assumem o domínio) e subir o `uai-ooh-intel` no ar
  (EP2-09/deploy) p/ os selos passarem a carimbar números **reais**. Pendência operacional carregada:
  **padronizar o runner de testes** (`bun`/vitest no CI) p/ não depender de `npx`/`npm` ad-hoc.
