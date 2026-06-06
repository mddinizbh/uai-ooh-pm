# Run — EP4-04 (front: mapa interativo · corredor + toggles) · 2026-06-06 · ✅ DONE

> Execução no `uai-portal` (ex-`uai-spark`, branch `feat/ooh-ep4-04`), plugando o **mapa MapLibre** no
> `mapSlot` da ficha (encaixe deixado pronto no EP4-03). Renderiza **trajeto · corredor 300m · pontos ·
> POIs por categoria** consumindo **só GeoJSON do intel** (`useLineGeo` → `GET /api/lines/{id}/geo`,
> camadas servidas no EP2-08), com **toggles por camada e por categoria de POI**. O componente é
> **derivado do legado** `uai-buslines-web` (tag `legacy-frozen`) — `mapLayers.ts`/`MapView.tsx`
> recuperados e adaptados, **não reescritos do zero** — e o mapa entra **lazy** (`React.lazy`) p/ não
> pesar o bundle da ficha. Stack React 18 · TS · **maplibre-gl** · shadcn/ui. Tarefa **só de front** —
> **não toca o banco `ooh`** (validação de DB vazia por design). Task do mapa F1:
> `docs/epicos/bloco1/f1/03-front/modulo-ooh/EP4-04-mapa-interativo.md`.
>
> **Veredito:** os **3 critérios de pronto** passam, com os gates **reproduzidos** (não só alegados):
> `npx tsc --noEmit -p tsconfig.app.json` ⇒ **exit 0** (zero erros de tipo); `npm run build`
> (`vite build` v5.4.19) ⇒ **BUILD SUCCESS** em **1,84 s** com o **maplibre code-split num chunk próprio**
> (`dist/assets/CorridorMap-*.js` **808 kB**, separado do `index-*.js` **451 kB**) — prova empírica do
> isolamento via `React.lazy`; suíte cheia do portal **98/98** (0 falhas, 0 skips), dos quais **24 novos
> do EP4-04** (`corridorLayers` 13 + `CorridorMap` 6 + `CorridorMapSlot` 5) e **3 existentes** do
> `PlanejamentoPage` reverificados. Review **aprovou** (verdict APROVADO); a rodada de refute **não
> derrubou** o done. → **DONE**. O mapa fecha a tríade visual da ficha (header+seções no EP4-03, mapa
> aqui) e deixa o `chartsSlot` p/ o EP4-05.

## Critério de pronto vs. medido (working tree `uai-portal` @ `feat/ooh-ep4-04`)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| Mapa renderiza **trajeto + corredor 300m + pontos + POIs** consumindo **só GeoJSON do intel** | 4 camadas a partir de GeoJSON pronto | `corridorLayers.ts:addCorridorLayers` adiciona as **4 camadas** — trajeto (`line`) · corredor (`fill`+`line` do buffer) · pontos (`circle`) · POIs por categoria (`circle`, cor por categoria); fonte única `useLineGeo` → `GET /api/lines/{id}/geo` (camadas do EP2-08). `CorridorMap.test.tsx` confirma sources/layers de trajeto/corredor/pontos + `poi:comercio`/`poi:alimentacao` + `fitBounds` | ✅ |
| **Toggles** funcionam por camada **e** por categoria de POI | visibilidade ligável por camada e por categoria | toggles por **camada** (trajeto/corredor/pontos) **e** por **categoria de POI** — testados; reusa o padrão de visibilidade do legado (chips `data-k`/`VIS[k]`) | ✅ |
| Componente **derivado do legado** (não reescrito do zero) | porte de `uai-buslines-web@legacy-frozen` | cabeçalhos documentam a derivação de `mapLayers.ts`/`MapView.tsx`; **mock maplibre recuperado do `legacy-frozen`** p/ os testes | ✅ |
| **Sem `ST_*`/PostGIS no front** (ADR-003) | só renderiza GeoJSON pronto | `corridorBounds` **varre as coords** do GeoJSON p/ o bbox (`fitBounds`) — **nenhum `ST_*`/PostGIS** no front; geometria chega pronta do intel | ✅ |
| Mapa **não pesa o bundle da ficha** | carregamento sob demanda | `React.lazy` ⇒ maplibre sai **code-split** num chunk próprio (`CorridorMap-*.js` **808 kB**) **separado** do `index-*.js` (**451 kB**) — confirmado no `vite build` | ✅ |
| Portal compila + contrato TS tipa | build OK, sem erros de tipo | `npm run build` ⇒ **BUILD SUCCESS, 1,84 s**; `npx tsc --noEmit -p tsconfig.app.json` ⇒ **exit 0** | ✅ |

## Build / testes (escopo declarado)

| Gate | Comando | Resultado | Veredito |
|---|---|---|---|
| Typecheck | `npx tsc --noEmit -p tsconfig.app.json` | **exit 0** — zero erros de tipo | ✅ |
| Build | `npm run build` (`vite build` v5.4.19) | **✓ built in 1,84 s**; maplibre em chunk próprio `CorridorMap-*.js` **808 kB** vs `index-*.js` **451 kB** | ✅ |
| Testes novos (EP4-04) | `vitest` (recorte) | **24/24** — `corridorLayers` **13** + `CorridorMap` **6** + `CorridorMapSlot` **5** (24 criados = 24 esperados) | ✅ |
| Regressão (slot da ficha) | `vitest` `PlanejamentoPage.test` | **3/3** — sem quebra do encaixe do mapSlot | ✅ |
| Suíte cheia | `npx vitest run` | **98/98** (0 falhas, 0 skips) — **+24** vs **74/74** do EP4-03 | ✅ |

- **Aviso de chunk >500 kB** no build é só **warning** (maplibre é pesado por natureza), **não falha** — e é
  exatamente o sintoma esperado do **code-split** que isola o mapa do bundle principal.
- **Toolchain:** runner via `npx vitest`/`npm` (fallback — `bun` ausente; `package.json` `test = "vitest
  run"`). Pendência operacional **viva desde EP3-01/EP4-01**: padronizar o runner (instalar `bun` ou fixar
  `vitest` no CI) p/ não depender de `npx`/`npm` ad-hoc.

## Decisões / desvios

- **Fonte única `/geo` em vez de splitar `/lines/{id}` + EP2-08:** a task previa trajeto+pontos de
  `GET /api/lines/{id}` e corredor+POIs do endpoint do EP2-08. A implementação **consolidou tudo no
  `GET /api/lines/{id}/geo`** (FeatureCollection do EP2-08) via `useLineGeo` — uma chamada, um payload
  GeoJSON, menos round-trips. **Não muda o contrato visual** (4 camadas iguais) e mantém o front como
  consumidor read-only de GeoJSON pronto. *(Desvio benigno; alinhado ao "lazy `/geo`" decidido no EP4-03.)*
- **Mapa via `React.lazy` (`CorridorMapSlot`):** o `CorridorMapSlot` faz o `lazy import` do `CorridorMap`,
  então o maplibre **só baixa quando a ficha abre e o slot monta** — daí o chunk `CorridorMap-*.js` 808 kB
  separado. Mantém a ficha leve (EP4-03 abre só com os números do `/metrics`). *(Alternativa descartada:
  importar maplibre no bundle principal.)*
- **Derivação do legado, não reescrita:** `mapLayers.ts`→`corridorLayers.ts` e `MapView.tsx`→`CorridorMap.tsx`
  recuperados de `uai-buslines-web@legacy-frozen` e adaptados p/ consumir o GeoJSON do intel; o **mock
  maplibre** dos testes também veio do `legacy-frozen`. Honra o critério "derivado do legado" e o CLAUDE.md
  do hub (recuperar via `git checkout legacy-frozen`).
- **bbox no cliente (`corridorBounds`):** o `fitBounds` é calculado **varrendo as coords** do GeoJSON no
  front, **sem** `ST_*`/PostGIS — respeitando o ADR-003 (geometria como GeoJSON pronto, classificação
  precomputada no intel).

## Contract-check back↔front (review)

- **GeoJSON do corredor:** `useLineGeo` → `GET /api/lines/{id}/geo` casa com o endpoint de camadas do
  `uai-ooh-intel` (**EP2-08**), que serve a FeatureCollection do que o **EP1-02** materializa (trajeto,
  buffer 300m, pontos, POIs por categoria). As 4 camadas do `addCorridorLayers` mapeiam 1:1 as features
  servidas.
- **Categorias de POI:** as chaves de toggle/cor (comércio · alimentação · saúde · educação) batem com a
  classificação de categoria do intel — `CorridorMap.test.tsx` exercita `poi:comercio`/`poi:alimentacao`.
- **Encaixe na ficha:** o mapa entra pelo `mapSlot` (`ooh-ficha-map-slot`, seção **Trajeto**) deixado pronto
  no EP4-03 — sem acoplar a ficha ao maplibre (o slot recebe `CorridorMapSlot` lazy).

## Estado do `dataset_version`

- **N/A — tarefa de front.** O `uai-portal` é o shell e **não acessa o banco `ooh`** nesta task; a
  validação de DB veio **vazia por design** (`db.ok = true`, sem checks). O GeoJSON do corredor chega ao
  front **via API do `uai-ooh-intel`** (consumidor read-only do `serving` ACTIVE). Nenhum ciclo de versão é
  tocado aqui — promoção do `dataset_version` segue pendência do EP1/serving, não daqui. Os shapes/POIs
  reais só materializam no mapa com o intel no ar (EP2-08/EP2-09/deploy).

## Adversarial — o que o cético tentou (refuted: false)

O vetor foi confrontado contra a **working tree atual** do `uai-portal`; **não derrubou** o done.

- **Vetor 1 (GATE/critério) — "os 3 critérios de pronto da EP4-04 não batem no código real".** **Não
  derrubou:** **(a)** as **4 camadas só de GeoJSON** existem — `corridorLayers.ts:addCorridorLayers` adiciona
  trajeto (`line`) · corredor (`fill`+`line`) · pontos (`circle`) · POIs por categoria (`circle`), com fonte
  única `useLineGeo` → `GET /api/lines/{id}/geo`; `CorridorMap.test.tsx` confirma sources/layers de
  trajeto/corredor/pontos + `poi:comercio`/`poi:alimentacao` + `fitBounds`. **(b)** **toggles por camada e
  por categoria** testados (padrão de visibilidade do legado). **(c)** **derivado do legado** — cabeçalhos
  documentam a derivação de `mapLayers.ts`/`MapView.tsx` e o mock maplibre veio do `legacy-frozen`. **ADR-003
  respeitado:** `corridorBounds` varre coords p/ bbox, **sem `ST_*`/PostGIS** no front. Reforço: `tsc
  --noEmit -p tsconfig.app.json` **exit 0**; `vitest run` **98/98 AGORA**.
- **Reforço empírico do code-split:** o `vite build` separa o maplibre num chunk próprio (`CorridorMap-*.js`
  **808 kB** vs `index-*.js` **451 kB**) — o `React.lazy` **funciona de fato**, não só na intenção; o warning
  de chunk >500 kB é cosmético, não falha.
- **Limite reconhecido (não derruba):** trajeto/corredor/POIs reais só aparecem no mapa com o `uai-ooh-intel`
  no ar (EP2-08/EP2-09/deploy); o que está provado é o **wiring** (`useLineGeo` → `/geo`, 4 camadas, toggles)
  por código e teste — não um render contra GeoJSON servido ao vivo.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/epicos/runs/EP4-04-mapa.md`).
- Código no repo-alvo: `uai-portal` (ex-`uai-spark`) @ `feat/ooh-ep4-04` — módulo `/ooh`:
  `corridorLayers.ts` (`addCorridorLayers`/`corridorBounds`, derivado de `mapLayers.ts`), `CorridorMap.tsx`
  (maplibre, derivado de `MapView.tsx`), `CorridorMapSlot.tsx` (lazy wrapper plugado no `mapSlot` da ficha),
  hook `useLineGeo` (`GET /api/lines/{id}/geo`); testes `corridorLayers.test` (13) · `CorridorMap.test.tsx`
  (6) · `CorridorMapSlot` (5) + mock maplibre recuperado do `legacy-frozen`.
- Próximo: **EP4-05** (charts → `chartsSlot`). Pendência operacional carregada: **padronizar o runner de
  testes** (`bun`/vitest) p/ não depender de `npx`/`npm` ad-hoc.
