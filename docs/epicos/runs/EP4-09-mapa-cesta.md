# Run — EP4-09 (front: mapa da cesta · alcance combinado) · 2026-06-06 · ✅ DONE

> Execução no `uai-portal` (ex-`uai-spark`, branch `feat/ooh-ep4-09`), entregando o **mapa da cesta** — um
> botão **🗺️ "ver mapa da cesta"** no **drawer da cesta** (EP4-06) que abre um `Dialog` plotando **todas as N
> linhas da cesta** em **cores distintas + legenda**, pro planejador ver a **cobertura/alcance combinado**. O
> mapa **reusa** o componente MapLibre do **EP4-04** (`CestaMap`/`CestaMapSlot` análogos a `CorridorMap`,
> derivados do legado) e consome **só GeoJSON do intel** — `/lines/{id}/geo` (EP2-08) buscado por linha pras N
> da cesta. **Trajetos sempre visíveis**; o **overlay de corredor 300m entra por TOGGLE com padrão OFF**
> (decisão resolvida 2026-06-05 — evita poluição quando os corredores se sobrepõem; reusa o padrão de toggle
> do EP4-04). O **snapshot** do mapa (`map.getCanvas().toDataURL('image/png')`, mapa criado com
> **`preserveDrawingBuffer: true`**) **reflete o estado atual do toggle** e é **roteado pro payload de export**
> (`mapaSnapshot` no contrato `ProposalPort.Cesta`, `ProposalPort.ts:28`) — o **EP4-07** (export PDF) é **stub
> reconhecido na própria task**, então aqui se **gera + roteia** o snapshot e o PDF real pluga depois. Stack
> React 18 · TS · **maplibre-gl** · shadcn/ui (`dialog`). Tarefa **só de front** — **não toca o banco `ooh`**
> (validação de DB vazia por design). Task do card:
> `docs/epicos/bloco1/f1/03-front/modulo-ooh/EP4-09-mapa-cesta.md`.
>
> **Veredito:** os **3 critérios de pronto** passam, com os gates **reproduzidos** (não só alegados): `npx
> vitest run` (fallback — `bun` ausente) ⇒ **140/140** (0 falhas, 0 skips), dos quais **18 novos do EP4-09**;
> recorte alvo **26/26** (`cestaLayers` 7 · `CestaMap` 7 · `CestaMapSlot` 4 · `Cesta` 8) + **regressão
> mapa/intel 20/20**; `tsc --noEmit` **exit 0**; `vite build` **OK** (2.569 módulos, 2,84 s) com o `CestaMap`
> **code-split num chunk lazy próprio** (`CestaMap-*.js` **3,61 kB**) e o **maplibre-gl isolado** no chunk de
> **801 kB** (split do EP4-04 preservado); **ESLint exit 0** nos arquivos novos/modificados. Review
> **aprovou** (verdict APROVADO — checklist 100% coberto); o refute **não derrubou** nenhum vetor. → **DONE**.
> Fecha o **mapa da cesta** e entrega ao **EP4-07** o `mapaSnapshot` já roteado pro export. **Ressalva honesta
> de toolchain:** o **estágio implement** rodou só o **recorte alvo (26)** e marcou a suíte cheia como "não
> rodada"; o **estágio Test** rodou a **suíte cheia 140/140** — reconciliado abaixo (não é contradição, é
> ordem de estágios).

## Critério de pronto vs. medido (working tree `uai-portal` @ `feat/ooh-ep4-09`)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| **Botão no drawer** abre o mapa com **todas** as linhas da cesta (cores + legenda) | botão → mapa, N linhas em cores distintas + legenda | `Cesta.tsx:239` botão `ooh-cesta-map-open` abre `Dialog` (shadcn) → `CestaMapSlot` (lazy) → `CestaMap.tsx:157-169` plota **cada** linha com `cestaColor` **por índice** (cores distintas) + legenda; fonte por linha `/lines/{id}/geo` (EP2-08) pras N da cesta | ✅ |
| **Trajetos sempre + corredor 300m por TOGGLE (padrão OFF)** | abre limpo (só trajetos); overlay liga/desliga; reusa padrão EP4-04 | overlay de corredor 300m com toggle **default OFF**; trajetos sempre visíveis; reusa o padrão de visibilidade do EP4-04 — coberto em `cestaLayers`/`CestaMap` | ✅ |
| **Snapshot (PNG)** gerado e **roteado pro export** (EP4-07) | canvas→PNG com `preserveDrawingBuffer`, ligado ao payload de export | `map.getCanvas().toDataURL('image/png')` com mapa criado `preserveDrawingBuffer: true`; snapshot **reflete o estado do toggle** e flui pro `mapaSnapshot` do payload via `ProposalPort.Cesta` (`ProposalPort.ts:28`); EP4-07 = stub reconhecido (gera+roteia aqui, PDF real depois) | ✅ |
| **Marcação de honestidade** (cobertura ≠ alcance único) | nota OTS, não alcance único | marcação **OTS (corredores que se sobrepõem) ≠ alcance único** presente — coerente com o COMBINADO (EP2-05) | ✅ |
| **Reuso do mapa do EP4-04** (não reescrito) | MapLibre do EP4-04 + `/geo` (EP2-08) + shadcn | `CestaMap`/`CestaMapSlot`/`cestaLayers` análogos a `CorridorMap`/`CorridorMapSlot`/`corridorLayers`; `Dialog` shadcn; nenhuma lib nova | ✅ |
| Portal compila + tipa + lint limpo | build OK, sem erros de tipo, ESLint 0 | `vite build` **OK** (2.569 módulos, 2,84 s); `tsc --noEmit` **exit 0**; **ESLint exit 0** nos arquivos novos/modificados | ✅ |

## Build / testes (escopo declarado)

| Gate | Comando | Resultado | Veredito |
|---|---|---|---|
| Suíte cheia | `npx vitest run` (fallback — `bun` ausente) | **140/140** (0 falhas, 0 skips) — **+18** novos do EP4-09 | ✅ |
| Recorte alvo (EP4-09) | `vitest` (recorte) | **26/26** — `cestaLayers` **7** · `CestaMap` **7** · `CestaMapSlot` **4** · `Cesta` **8** | ✅ |
| Regressão (mapa/intel) | `vitest` (recorte) | **20/20** — sem quebra do mapa do EP4-04 nem do `intelClient` | ✅ |
| Typecheck | `tsc --noEmit` | **exit 0** — zero erros de tipo | ✅ |
| Lint | ESLint (arquivos novos/modificados) | **exit 0** | ✅ |
| Build | `vite build` | **OK** — 2.569 módulos, 2,84 s; `CestaMap-*.js` **3,61 kB** (chunk lazy próprio) e **maplibre-gl** isolado no chunk de **801 kB** (code-split do EP4-04 **preservado**) | ✅ |
| DB | MCP `postgres-ooh` | **vazio por design** (`db.ok = true`, sem checks) — tarefa de front | ✅ |

- **Reconciliação de estágios (não é contradição):** o **estágio implement** rodou só o **recorte alvo (26)** e
  registrou a suíte cheia como **"não rodada"** naquele momento; o **estágio Test** subsequente rodou a **suíte
  cheia ⇒ 140/140** (18 novos). A asserção final de done usa o **140/140 do Test**. O refute, por sua vez, contou
  o **módulo `/ooh`** num corte próprio (**18 arquivos / 110 testes verdes** + `tsc` exit 0) — cortes diferentes,
  todos **verdes AGORA**, nenhum em conflito.
- **Toolchain:** runner via `npx vitest` (**fallback** — `bun` ausente no ambiente; `package.json` `test =
  "vitest run"`). Pendência operacional **viva desde EP3-01/EP4-01/EP4-04/EP4-05/EP4-06**: padronizar o runner
  (instalar `bun` ou fixar `vitest` no CI) p/ não depender de `npx`/`npm` ad-hoc.
- **Aviso de chunk >500 kB** do maplibre segue **só warning** (esperado, é o sintoma do code-split que isola o
  mapa) — **não falha**.

## Decisões / desvios

- **Trajetos sempre + corredor 300m por TOGGLE, padrão OFF (decisão da task confirmada, 2026-06-05):** o mapa
  abre **limpo** (só trajetos das N linhas); o usuário **liga o toggle** pra ver o overlay de corredor (a faixa
  de cobertura). Evita poluição visual quando os corredores se sobrepõem. Reusa `/geo` (que já traz o corredor)
  e o padrão de toggle do EP4-04. *(Alternativa descartada: corredores sempre ligados ⇒ "borrão" no centro.)*
- **`preserveDrawingBuffer: true` no mapa da cesta (custo aceito):** o `toDataURL` do canvas WebGL **só captura
  pixels** se o buffer não foi descartado — daí criar o `CestaMap` com `preserveDrawingBuffer: true`. Trade-off:
  leve custo de performance no render, **necessário** p/ o snapshot funcionar de fato (não só na intenção). O
  snapshot **reflete o estado atual do toggle** (corredor on/off) no momento da captura.
- **`CestaMap` lazy em chunk próprio (split preservado):** `CestaMapSlot` faz o `lazy import` do `CestaMap` ⇒
  `CestaMap-*.js` **3,61 kB** separado, e o **maplibre-gl** permanece **isolado** no chunk de **801 kB** já
  estabelecido no EP4-04 — o mapa da cesta **não** repesa o bundle principal nem duplica maplibre.
- **Snapshot roteado pro `ProposalPort.Cesta.mapaSnapshot` (fronteira pro EP4-07):** esta task **gera + roteia**
  o PNG pro `mapaSnapshot` do payload de export; o **EP4-07** (export PDF) é **stub reconhecido na própria
  task**, então o **arquivo PDF real** chega lá — aqui o que se prova é o **campo no contrato + o fluxo**
  (snapshot → payload), não a renderização do PDF. *(Coerente com o EP4-06, que já isolou a `ProposalPort`.)*
- **Reuso do EP4-04 (não reescrita):** `cestaLayers`/`CestaMap`/`CestaMapSlot` espelham
  `corridorLayers`/`CorridorMap`/`CorridorMapSlot`; a diferença é plotar **N linhas com cor por índice** (vs. 1
  linha na ficha). Sem lib nova; `Dialog` shadcn já no portal.

## Honestidade do mapa da cesta (EP2-05 / coerência com o COMBINADO)

- A cobertura plotada são **corredores que se sobrepõem** = **OTS somado**, **não alcance único** —
  sobreposições de público **não** descontadas. A marcação de honestidade está presente (OTS ≠ alcance único),
  alinhada ao COMBINADO do EP4-06 e ao EP4-08 (honestidade de UI). O snapshot que vai pro PDF carrega o **mesmo
  recorte honesto** do que está na tela.

## Contract-check back↔front (review)

- **GeoJSON por linha da cesta:** `/lines/{id}/geo` (EP2-08) buscado **por linha** pras N da cesta — mesma fonte
  do EP4-04 (trajeto + corredor 300m vêm prontos do intel; o front só renderiza). Nenhum campo novo pedido.
- **Fronteira interna `ProposalPort.Cesta.mapaSnapshot`:** o snapshot **não** chama o back — é roteado pro
  **payload de export** via o campo `mapaSnapshot` do contrato `ProposalPort.Cesta` (`ProposalPort.ts:28`), o
  ponto exato onde o **EP4-07** pluga o PDF real. Adapter de export segue **local F1** (stub, EP4-06).
- **Estado da cesta preservado:** o mapa lê as N linhas do `CestaContext` (EP4-06) sem alterar a
  `CestaSelection` — drawer/barra/lista/ficha **não mudam de assinatura**.

## Estado do `dataset_version`

- **N/A — tarefa de front.** O `uai-portal` é o shell e **não acessa o banco `ooh`** nesta task; a validação de
  DB veio **vazia por design** (`db.ok = true`, sem checks). O GeoJSON das linhas da cesta chega ao front **via
  API do `uai-ooh-intel`** (`/lines/{id}/geo`, consumidor read-only do `serving` ACTIVE). Nenhum ciclo de versão
  é tocado aqui — promoção do `dataset_version` segue pendência do EP1/serving, não daqui. Os trajetos/corredores
  reais só materializam no mapa (e no snapshot) com o intel no ar (EP2-08/EP2-09/deploy).

## Adversarial — o que o cético tentou (refuted: false)

O vetor foi confrontado contra a **working tree atual** do `uai-portal`; **não derrubou** o done. O refute não
conseguiu refutar **nenhum** vetor com evidência concreta — os 3 critérios estão cumpridos no working tree e
cobertos por testes **verdes AGORA** (módulo `/ooh`: **18 arquivos / 110 testes verdes**; `tsc --noEmit` **exit
0**).

- **Vetor 1 (GATE/critério) — "os 3 critérios de pronto da EP4-09 não batem no código real" (botão→mapa com N
  linhas+legenda; snapshot PNG roteado pro export; marcação de honestidade).** **Não derrubou:** **(a)** o
  **botão** `ooh-cesta-map-open` (`Cesta.tsx:239`) abre `Dialog` → `CestaMapSlot` → `CestaMap` que plota **todas**
  as linhas em **cores distintas** (`CestaMap.tsx:157-169`, `cestaColor` por índice) **+ legenda**. **(b)** o
  **snapshot PNG** é gerado por `map.getCanvas().toDataURL('image/png')` com `preserveDrawingBuffer: true` e
  **roteado** pro `mapaSnapshot`, campo **presente no contrato** `ProposalPort.Cesta` (`ProposalPort.ts:28`).
  **(c)** **honestidade** OTS ≠ alcance único presente. Reforço: `vitest run` **140/140 AGORA** (18 novos),
  recorte alvo **26/26**, regressão **20/20**, `tsc` **exit 0**, build com `CestaMap` lazy (3,61 kB) e maplibre
  isolado (801 kB).
- **Vetor 2 (toolchain/coerência de números) — "implement diz suíte não rodada".** **Não derrubou:** é **ordem
  de estágios** — o implement rodou só o recorte alvo (26) e o **Test** rodou a **suíte cheia 140/140**; o refute
  contou o módulo `/ooh` (18 arquivos / 110 testes). Cortes diferentes, todos verdes, sem conflito.
- **Limite reconhecido (não derruba):** o **PDF real** chega no **EP4-07** (stub reconhecido) — o que está
  provado aqui é o **snapshot gerado + roteado pro `mapaSnapshot`** (campo no contrato + fluxo), não o PDF
  renderizado; e os trajetos/corredores **reais** só aparecem no mapa com o `uai-ooh-intel` no ar
  (EP2-08/EP2-09/deploy) — o provado é o **wiring** (`/geo` por linha, N camadas, toggle, snapshot) por código e
  teste, não um render contra GeoJSON servido ao vivo.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/epicos/runs/EP4-09-mapa-cesta.md`).
- Código no repo-alvo: `uai-portal` (ex-`uai-spark`) @ `feat/ooh-ep4-09` — módulo `/ooh`: `cestaLayers.ts`
  (camadas das N linhas, cor por índice, overlay de corredor com toggle), `CestaMap.tsx` (maplibre,
  `preserveDrawingBuffer: true`, `getCanvas().toDataURL` p/ snapshot), `CestaMapSlot.tsx` (lazy wrapper),
  botão/`Dialog` em `Cesta.tsx` (`ooh-cesta-map-open`), campo `mapaSnapshot` em `ProposalPort.ts` (`Cesta`);
  testes `cestaLayers` (7) · `CestaMap` (7) · `CestaMapSlot` (4) · `Cesta` (8) — **18 novos** na suíte (140/140).
- Próximo: **EP4-07** (export PDF — pluga o adapter real e consome o `mapaSnapshot` já roteado), **EP4-08**
  (honestidade de UI). Pendência operacional carregada: **padronizar o runner de testes** (`bun`/vitest no CI)
  p/ não depender de `npx`/`npm` ad-hoc.
</content>
</invoke>
