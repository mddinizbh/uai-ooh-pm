# Run — EP4-02 (front: lista + filtros do ranking) · 2026-06-06 · ✅ DONE

> Execução no `uai-portal` (ex-`uai-spark`, branch `feat/ooh-ep4-02`), montando a **esquerda do
> master-detail** do módulo `/ooh`: lista enxuta ordenável (`nº · linha · score`) + painel de filtros
> (público AB/DE · cascata região→bairro · 5 sliders de peso + reset) + quick-add **＋ cesta direto da
> linha**, tudo alimentado por **um endpoint só** (`GET /api/lines/ranking`) sobre o `intelClient`
> (EP4-01). Stack React 18 · TS · shadcn/ui · `@tanstack/react-query`. Tarefa **só de front** — **não
> toca o banco `ooh`** (validação de DB vazia por design). Task do mapa F1:
> `docs/epicos/bloco1/f1/03-front/modulo-ooh/EP4-02-lista-filtros.md`.
>
> **Veredito:** o critério de pronto passa nos 3 itens, com **todos os gates reproduzidos** (não só
> alegados): `npx tsc --noEmit -p tsconfig.app.json` ⇒ **exit 0**; `npx vitest run src/modules/ooh` ⇒
> **34/34** (21 novos do EP4-02 + 13 herdados do EP4-01); suíte cheia do portal **64/64** (0 falhas, 0
> skips); `npx eslint` nos **5 arquivos novos** ⇒ **exit 0**; `npm run build` (`vite build`, fallback de
> `bun` ausente como previsto) ⇒ **BUILD SUCCESS, 1748 módulos** transformados em ~1,2 s (vs **1688** no
> EP4-01). A lista é servida só por `/ranking` (`PlanejamentoPage` → `useRankingControls`/`useRanking`),
> renderiza `rankings.length` linhas ordenado por score, a cascata região→bairro funciona (bairro fica
> **desabilitado** até ter região) e clicar **seleciona** (`data-selected`/`onSelect`) enquanto o **＋**
> adiciona à cesta (`cesta.toggle` com `stopPropagation`). Review **aprovou**; a rodada de refute **não
> derrubou** o done em nenhum dos 3 vetores. → **DONE**. A seleção já está pronta p/ **dirigir a ficha**
> (EP4-03).

## Critério de pronto vs. medido (working tree `uai-portal` @ `feat/ooh-ep4-02`)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| Lista mostra as **303** ordenadas por score; reordena com pesos/público/região via `/ranking` | lista ordenável servida por **um endpoint só** | `PlanejamentoPage` consome `useRankingControls`/`useRanking` → **`GET /api/lines/ranking`** com os params atuais; `RankingList` renderiza `rankings.length` linhas (`nº · linha · score`) já ordenado por score. **303** = alvo de contrato (`core.line_metrics`, épico 3), não count de chamada ao vivo | ✅¹ |
| Cascata região→bairro funciona (de `/regions`) | cascata real, bairro dependente da região | `FiltersPanel` **desabilita o select de bairro até ter região** escolhida; opções de `/regions` via `intelClient`. Comportamento coberto por teste | ✅ |
| Clicar **seleciona** (dirige a ficha); **＋** adiciona à cesta | seleção + quick-add por linha | clique na linha emite `onSelect` e marca `data-selected`; botão **＋** chama `cesta.toggle` com **`stopPropagation`** (não dispara o select da linha); estado do botão reflete ＋ / ✓ na cesta (`useCestaSelection`) | ✅ |
| Portal compila | build de produção OK | `npm run build` (`vite build`) ⇒ **1748 módulos** em ~1,2 s; `dist/` gerado | ✅ |
| Contrato TS tipa | sem erros de tipo | `npx tsc --noEmit -p tsconfig.app.json` ⇒ **exit 0** (rodado à parte — o `vite build` não tipa) | ✅ |

> ¹ Mesmo asterisco carregado do EP4-01: o **303** é o número de contrato (total de linhas em
> `core.line_metrics`, validado no épico 3), **não** um count retornado por chamada HTTP ao vivo nesta
> run. O que está provado é o **wiring** (`useRanking` → `/api/lines/ranking` com os params) por código e
> teste; o número real só materializa com o `uai-ooh-intel` no ar (EP2-09/deploy). Não derruba o done.

## Build / testes (escopo declarado)

- **Build:** `npm run build` (`vite build`) ⇒ **BUILD SUCCESS**, **1748 módulos** transformados em ~1,2 s
  (vs **1688** no EP4-01 — +60 do `RankingList`/`FiltersPanel`/shadcn). Fallback p/ `npm` **previsto pelo
  comando** (`bun` ausente; `bun` ⇒ exit 127).
- **Typecheck:** `npx tsc --noEmit -p tsconfig.app.json` ⇒ **exit 0**. Rodado **à parte de propósito**: o
  `vite build` não faz typecheck, então o `tsc` garante o contrato TS do módulo.
- **Lint:** `npx eslint` nos **5 arquivos novos** ⇒ **exit 0** (sem warnings).
- **Testes (recorte do módulo):** `npx vitest run src/modules/ooh` ⇒ **34/34** (0 falhas) = **21 novos do
  EP4-02 + 13 herdados do EP4-01**.
- **Testes (suíte cheia):** `npx vitest run` ⇒ **64/64** (0 falhas, 0 skips) — **sem regressão** no portal
  legado (marketing). **4 arquivos de teste novos criados = 4 esperados**.

## Decisões / desvios

- **UI dos pesos = 5 sliders + reset** (decisão da task p/ F1): controle fino dos sub-scores + voltar ao
  default. Presets "Alcance-first/Perfil-first" ficam **pós-F1**. Implementado em `FiltersPanel` +
  `useRankingControls` (estado dos pesos vira params do `/ranking`).
- **Um endpoint só pra lista** (`GET /api/lines/ranking`): pesos + público + região/bairro entram como
  **query params**; sem params = ordem do score base. Nenhuma reordenação client-side — a lista é sempre
  o que o intel devolve. Mantém a fonte de verdade no back.
- **Quick-add com `stopPropagation`**: o **＋** adiciona à cesta **sem** disparar o `onSelect` da linha —
  resolve o conflito clique-na-linha (seleciona) × clique-no-botão (adiciona). Estado do botão (＋ / ✓)
  vem do `useCestaSelection`.
- **Bairro desabilitado até ter região**: a cascata é guard-rail de UX (evita filtro de bairro órfão sem
  região). Decisão de implementação, alinhada ao critério.
- **Limpeza — `OohPlanningPage` morto removido**: a página antiga foi deletada; `tsc --noEmit` exit 0
  confirma **zero refs órfãs**. Não é regressão — é faxina do scaffold.
- **Toolchain de teste — `bun` ausente** (pendência viva desde EP3-01/EP4-01): suíte roda via
  `npx vitest`/`npm` como fallback. Continua a pendência operacional: **padronizar o runner**
  (instalar `bun` ou fixar `vitest` no CI) p/ não depender de `npx`/`npm` ad-hoc.

## Contract-check back↔front (review)

- **Lista**: `useRanking` casa com o controller de ranking do `uai-ooh-intel` — endpoint
  `GET /api/lines/ranking` e os params (pesos × 5 + público + região/bairro) batem com o contrato.
- **Cascata**: `FiltersPanel` consome `/regions` (regiões → bairros) pelo mesmo `intelClient` do EP4-01;
  campos camelCase de `Region` batem 1:1 (back sem `PropertyNamingStrategy` snake_case).
- **Tipos**: `LineRanking`/`Region` reusados do `types.ts` (espelho dos records do intel, ancorado no
  EP4-01) — nenhuma tradução de nome.

## Estado do `dataset_version`

- **N/A — tarefa de front.** O `uai-portal` é o shell e **não acessa o banco `ooh`** nesta task; a
  validação de DB veio **vazia por design** (`db.ok=true`, sem checks). Os dados de ranking/filtros chegam
  ao front **via API do `uai-ooh-intel`** (consumidor read-only do `serving` ACTIVE). Nenhum ciclo de
  versão é tocado aqui — promoção do `dataset_version` segue pendência do EP1/serving, não daqui.

## Adversarial — o que o cético tentou (refuted: false)

Os vetores foram confrontados contra a **working tree atual** do `uai-portal`; **nenhum derrubou** o done.

- **Vetor 1 (GATE/critério) — "os 3 critérios de pronto não batem no código real".** **Não derrubou:**
  em `src/modules/ooh/`, a lista é servida **só** por `GET /api/lines/ranking` (`PlanejamentoPage` →
  `useRanking`), renderiza `rankings.length` linhas **ordenado por score**; a cascata região→bairro
  funciona (`FiltersPanel` desabilita bairro até ter região); clicar **seleciona** (`data-selected`/
  `onSelect`) e o **＋** adiciona à cesta (`cesta.toggle` com `stopPropagation`). `tsc --noEmit` exit 0,
  **sem refs órfãs** ao `OohPlanningPage` deletado, suíte cheia **64/64 AGORA**.
- **Vetor 2 (contrato back↔front) — "o front chama params/endpoint que o intel não expõe".** **Não
  derrubou:** `useRanking` aponta pro `/api/lines/ranking` com os params do contrato (pesos × 5 + público
  + região/bairro) e a cascata consome `/regions`; tipos reusados do `types.ts` (espelho dos records do
  intel). Limite reconhecido (não derruba): o **303** é alvo de contrato, não count ao vivo — depende do
  intel no ar.
- **Vetor 3 (regressão/colateral) — "a lista quebrou algo do portal legado ou o quick-add dispara o
  select".** **Não derrubou:** **64/64** na suíte cheia (0 falhas, 0 skips), **sem regressão** no
  marketing; o **＋** usa `stopPropagation`, então quick-add **não** aciona o `onSelect` da linha — coberto
  por teste.
- **Reforço empírico:** `vitest run src/modules/ooh` **34/34** (21 EP4-02 + 13 EP4-01), `vitest run`
  **64/64**, `tsc --noEmit` **exit 0**, `eslint` nos 5 novos **exit 0** e `vite build` **OK (1748
  módulos)** — asseverando lista ordenável por `/ranking`, cascata região→bairro, seleção + quick-add e
  o contrato TS.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/epicos/runs/EP4-02-lista-filtros.md`).
- Código no repo-alvo: `uai-portal` (ex-`uai-spark`) @ `feat/ooh-ep4-02` —
  `src/modules/ooh/components/{RankingList,FiltersPanel}.tsx`,
  `src/modules/ooh/hooks/{useRankingControls,useCestaSelection}.ts`,
  `src/modules/ooh/pages/PlanejamentoPage.tsx` (esquerda do master-detail montada).
- Próximo: **EP4-03** (ficha — direita do master-detail, consome a seleção desta lista) e demais
  EP4-04..09. Pendência operacional carregada: **padronizar o runner de testes** (`bun`/vitest) p/ não
  depender de `npx`/`npm` ad-hoc.
