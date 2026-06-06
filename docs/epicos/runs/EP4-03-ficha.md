# Run — EP4-03 (front: ficha · master-detail) · 2026-06-06 · ✅ DONE

> Execução no `uai-portal` (ex-`uai-spark`, branch `feat/ooh-ep4-03`), montando a **direita do
> master-detail** do módulo `/ooh`: o **container da ficha** que, ao selecionar uma linha na lista
> (EP4-02), busca `/lines/{id}` + `/metrics` e renderiza a ficha no layout do **4107**. Split em dois
> componentes — `LineFicha.tsx` (orquestra o fetch via `useLineMetrics` + `useLineDetail`) e
> `LineFichaView.tsx` (apresentacional, **7 seções**) — com **slots** plugáveis p/ o mapa (EP4-04) e os
> charts (EP4-05). Stack React 18 · TS · shadcn/ui · `@tanstack/react-query`. Tarefa **só de front** —
> **não toca o banco `ooh`** (validação de DB vazia por design). Task do mapa F1:
> `docs/epicos/bloco1/f1/03-front/modulo-ooh/EP4-03-ficha.md`.
>
> **Veredito:** os **3 critérios de pronto** passam, com os gates **reproduzidos** (não só alegados):
> `npx tsc -p tsconfig.app.json --noEmit` ⇒ **exit 0** (zero erros de tipo nos arquivos tocados);
> `npm run build` (`vite build` v5.4.19) ⇒ **BUILD SUCCESS, 1754 módulos** transformados em ~1,3 s (vs
> **1748** no EP4-02); suíte cheia do portal **74/74** (0 falhas, 0 skips), dos quais **10 novos do
> EP4-03** (`LineFicha` 6 + `LineFichaView` 4) e **3 existentes** do `PlanejamentoPage` afetados pela
> troca do slot. Ao selecionar a linha, `LineFicha` dispara o fetch e renderiza header (nome/longName,
> badges **score · classe · %AB**, toggle de cesta, meta do catálogo) + as 7 seções com números reais
> do `/metrics`; mapa e charts entram por **slots** com placeholder; empty state + skeleton presentes e
> **box de honestidade** nas impressões. Review **aprovou** (verdict APROVADO); a rodada de refute
> **não derrubou** o done. → **DONE**. A ficha já consome a **seleção da lista** (EP4-02) e deixa os
> **encaixes prontos** p/ EP4-04 (mapa) e EP4-05 (charts).

## Critério de pronto vs. medido (working tree `uai-portal` @ `feat/ooh-ep4-03`)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| Selecionar uma linha carrega a ficha com **header + todas as seções** (números reais do `/metrics`) | ficha completa dirigida pela seleção | `LineFicha.tsx` orquestra `useLineMetrics` + `useLineDetail` ao receber a linha selecionada e renderiza `LineFichaView` com **header** (nome/longName, badges **score · classe · %AB**, toggle de cesta, meta do catálogo) + **7 seções** (trajeto · alcance · perfil renda/classe · demanda · arterial+POIs · impressões · análises) consumindo `reach`/`profile`/`demand`/`poi`/`arterial`/`impressions` | ✅¹ |
| **Slots** de mapa/charts presentes (preenchidos por EP4-04/05) | encaixes prontos, ainda vazios | `mapSlot` (testId `ooh-ficha-map-slot`, na seção **Trajeto**) e `chartsSlot` (`ooh-ficha-charts-slot`, na seção **Análises**) com placeholders, repassados de `LineFicha` → `LineFichaView` | ✅ |
| Empty state + skeleton; **box de honestidade** nas impressões | estados de borda + faixa indicativa marcada | empty state ("selecione uma linha") + skeleton no loading; seção de **impressões** com box de honestidade (faixa indicativa F1) | ✅ |
| Portal compila | build de produção OK | `npm run build` (`vite build` v5.4.19) ⇒ **1754 módulos** em ~1,3 s; `dist/` gerado | ✅ |
| Contrato TS tipa | sem erros de tipo | `npx tsc -p tsconfig.app.json --noEmit` ⇒ **exit 0** (rodado à parte — o `vite build` não tipa) | ✅ |

> ¹ Mesmo asterisco carregado do EP4-01/02: os números da ficha (score, %AB, alcance, POI/km, etc.) são
> **contrato** dos records do `uai-ooh-intel`, validados no épico 3 (`core.line_metrics`), **não** counts
> retornados por chamada HTTP ao vivo nesta run. O que está provado é o **wiring** (`useLineMetrics`/
> `useLineDetail` → `/lines/{id}` + `/metrics`) por código e teste; os valores reais só materializam com o
> intel no ar (EP2-09/deploy). Não derruba o done.

## Build / testes (escopo declarado)

- **Build:** `npm run build` (`vite build` v5.4.19) ⇒ **BUILD SUCCESS**, **1754 módulos** transformados
  em **1,30 s** (vs **1748** no EP4-02 — +6 do `LineFicha`/`LineFichaView`/shadcn).
- **Typecheck:** `npx tsc -p tsconfig.app.json --noEmit` ⇒ **exit 0**, **zero erros de tipo** nos arquivos
  tocados. Rodado **à parte de propósito**: o `vite build` não faz typecheck.
- **Testes (recorte do EP4-03):** `vitest` em `LineFicha.test.tsx` + `LineFichaView.test.tsx` ⇒ **10/10**
  (`LineFicha` **6** + `LineFichaView` **4**); `PlanejamentoPage.test.tsx` (**existente**, afetado pela
  troca do slot da ficha) ⇒ **3/3**.
- **Testes (suíte cheia):** `npx vitest run` ⇒ **74/74** (0 falhas, 0 skips) — **sem regressão** no portal
  e **sem quebra colateral** da troca `OohPlanningPage` → `PlanejamentoPage`. **10 testes novos criados =
  10 esperados.**
- **Toolchain:** runner via `npx vitest`/`npm` (fallback — `bun` ausente; `package.json` `test = "vitest
  run"`). Pendência operacional **viva desde EP3-01/EP4-01**: padronizar o runner (instalar `bun` ou fixar
  `vitest` no CI) p/ não depender de `npx`/`npm` ad-hoc.

## Decisões / desvios

- **Split container × apresentação:** `LineFicha.tsx` faz **só fetch/orquestração** (`useLineMetrics` +
  `useLineDetail`, estados de loading/empty) e `LineFichaView.tsx` é **apresentacional puro** (7 seções +
  slots). Mantém a view testável sem rede e isola o data-fetching — daí os testes separados (6 + 4).
- **Carregamento `/metrics` na hora, `/geo` lazy** (decisão da task): a ficha abre rápida com os números;
  o mapa+POIs (`/geo`, pesados no corredor) carregam **sob demanda** quando o `mapSlot` for preenchido
  pelo EP4-04. *(Alternativa descartada: carregar tudo de uma vez.)*
- **Slots como pontos de extensão nomeados:** `mapSlot` (`ooh-ficha-map-slot`, seção **Trajeto**) e
  `chartsSlot` (`ooh-ficha-charts-slot`, seção **Análises**) entram como props repassadas
  `LineFicha` → `LineFichaView`, com placeholder até EP4-04/05 plugarem — sem acoplar a ficha ao mapa/charts.
- **Troca do slot `OohPlanningPage` → `PlanejamentoPage`:** a ficha passou a ser montada na direita do
  master-detail dentro do `PlanejamentoPage`; os **3 testes existentes** desse arquivo foram
  reverificados (**3/3**) e a suíte cheia (**74/74**) confirma **zero quebra colateral**.
- **Box de honestidade nas impressões:** a seção de impressões carrega a **faixa indicativa** (fórmula F1
  simples), não número fechado — alinhado à honestidade-UI (escopo aprofundado depois no EP4-08).

## Contract-check back↔front (review)

- **Ficha**: `useLineDetail` / `useLineMetrics` casam com o controller de ficha do `uai-ooh-intel`
  (EP2-03) — endpoints `GET /lines/{id}` + `/metrics`; os blocos consumidos (`reach`/`profile`/`demand`/
  `poi`/`arterial`/`impressions`) batem com os records do intel.
- **Tipos**: DTOs da ficha reusados do `types.ts` do módulo (espelho dos records do intel, ancorado no
  EP4-01), camelCase 1:1 — nenhuma tradução de nome (back sem `PropertyNamingStrategy` snake_case).
- **Seleção**: a entrada da ficha é a linha selecionada na lista (EP4-02), sem navegação — troca de linha
  é instantânea (master-detail com lista slim ~260px / ficha ~75%).

## Estado do `dataset_version`

- **N/A — tarefa de front.** O `uai-portal` é o shell e **não acessa o banco `ooh`** nesta task; a
  validação de DB veio **vazia por design** (`db.ok = true`, sem checks). Os dados da ficha chegam ao
  front **via API do `uai-ooh-intel`** (consumidor read-only do `serving` ACTIVE). Nenhum ciclo de versão
  é tocado aqui — promoção do `dataset_version` segue pendência do EP1/serving, não daqui.

## Adversarial — o que o cético tentou (refuted: false)

O vetor foi confrontado contra a **working tree atual** do `uai-portal`; **não derrubou** o done.

- **Vetor 1 (GATE/critério) — "os 3 critérios de pronto da EP4-03 não batem no código real".** **Não
  derrubou:** **(a)** header + todas as seções com números reais do `/metrics` — `LineFicha.tsx`
  (≈ l.56–157) faz `useLineMetrics` + `useLineDetail` e renderiza header (nome/longName, badges
  **score · classe · %AB**, toggle de cesta, meta do catálogo) + `LineFichaView.tsx` com as **7 seções**
  (trajeto/alcance/perfil/demanda/arterial+POIs/impressões/análises) consumindo
  `reach`/`profile`/`demand`/`poi`/`arterial`/`impressions`. **(b)** Slots presentes: `mapSlot`
  (`ooh-ficha-map-slot`, seção Trajeto) e `chartsSlot` (`ooh-ficha-charts-slot`, seção Análises) com
  placeholders, repassados de `LineFicha` → `LineFichaView`. **(c)** Empty state + skeleton + box de
  honestidade nas impressões. Reforço: `tsc -p tsconfig.app.json --noEmit` **exit 0**, `vitest run`
  **74/74 AGORA**.
- **Limite reconhecido (não derruba):** os valores da ficha são **alvo de contrato** dos records do intel
  (épico 3), não count de chamada ao vivo — só materializam com o `uai-ooh-intel` no ar (EP2-09/deploy).
- **Reforço empírico:** `vitest` no recorte do EP4-03 ⇒ **10/10** (`LineFicha` 6 + `LineFichaView` 4) +
  `PlanejamentoPage` **3/3**; `vitest run` **74/74**; `tsc -p tsconfig.app.json --noEmit` **exit 0**;
  `vite build` v5.4.19 **OK (1754 módulos, 1,30 s)** — asseverando ficha dirigida pela seleção, header +
  7 seções dos números reais, slots de mapa/charts e o contrato TS, **sem regressão** da troca
  `OohPlanningPage` → `PlanejamentoPage`.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/epicos/runs/EP4-03-ficha.md`).
- Código no repo-alvo: `uai-portal` (ex-`uai-spark`) @ `feat/ooh-ep4-03` —
  `src/modules/ooh/components/{LineFicha,LineFichaView}.tsx` (container + view da ficha),
  `src/modules/ooh/pages/PlanejamentoPage.tsx` (direita do master-detail montada).
- Próximo: **EP4-04** (mapa interativo → plugа no `mapSlot`) e **EP4-05** (charts → `chartsSlot`).
  Pendência operacional carregada: **padronizar o runner de testes** (`bun`/vitest) p/ não depender de
  `npx`/`npm` ad-hoc.
