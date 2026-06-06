# Run — EP4-06 (front: cesta + COMBINADO + gancho de export) · 2026-06-06 · ✅ DONE

> Execução no `uai-portal` (ex-`uai-spark`, branch `feat/ooh-ep4-06`), entregando a **cesta** — a seleção
> **efêmera, client-only** de linhas — com o **COMBINADO** sempre à vista e o gancho de **export**. Resolve o
> dilema drawer-vs-painel com **dois estados**: **barra fixa** (`Cesta.tsx`, canto inferior direito, largura =
> a do drawer) que mostra o COMBINADO o tempo todo (`N linhas · impressões/dia · %AB`) **mesmo com o drawer
> fechado**, e **drawer** (shadcn `Sheet`) que abre o **per-linha + honestidade + exportar** sob demanda. O
> estado vive no `CestaContext` (React context + **persist em `localStorage`**, sobrevive a refresh, **sem
> backend**) e mantém a **mesma interface de consumo** (`CestaSelection`: `isInCesta`/`toggle`/`size`) já usada
> por `RankingList` (EP4-02) e `LineFicha` (EP4-03) — eles **não mudaram**, só ganharam os pontos de add/remove.
> O COMBINADO recalcula a cada ＋/remover via **`POST /api/lines/aggregate`** (stateless, contrato do EP2-05,
> consumido pelo `intelClient`/`useAggregate` do EP4-01). Export sai por trás da **`ProposalPort`** (porta
> hexagonal no front; adapter **local F1** = PDF client-side, stub do EP4-07). Stack React 18 · TS · shadcn/ui ·
> react-query. Tarefa **só de front** — **não toca o banco `ooh`** (validação de DB vazia por design). Task do
> card: `docs/epicos/bloco1/f1/03-front/modulo-ooh/EP4-06-cesta.md`.
>
> **Veredito:** os **3 critérios de pronto** passam, com a suíte **reproduzida** (não só alegada): `npx vitest
> run` ⇒ **119/119** (0 falhas, 0 skips), dos quais **13 novos do EP4-06** (`CestaContext` + `Cesta` +
> `ProposalPort`) e o restante (incl. os 106/106 do EP4-05) reverificado. **Retry aprovado:** a 1ª passada
> deixou **4 testes do `intelClient` vermelhos** — diagnosticado como **vazamento do `.env.local` da máquina**
> (`VITE_INTEL_BASE_URL=http://localhost:8087`, auto-carregado pelo Vite/Vitest, prefixando as URLs **relativas**
> asseridas), **não** um bug de produto; corrigido em **um único arquivo** (`vitest.config.ts` →
> `test.env.VITE_INTEL_BASE_URL = ""`, base vazia determinística). Review **aprovou** (causa-raiz e fix
> corretos); refute **não derrubou** o done. → **DONE**. Fecha o **primeiro card de ação** do EP4; destrava o
> **EP4-07** (export PDF), que pluga seu adapter real na `ProposalPort` já no lugar.

## Critério de pronto vs. medido (working tree `uai-portal` @ `feat/ooh-ep4-06`)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| **add/remove** linhas da **lista** e da **ficha** | ＋/remover dispara de ambas | `RankingList.tsx:140` e `LineFicha.tsx:96` chamam **`cesta.toggle(line)`** (mesmo contrato `CestaSelection`, telas inalteradas); `CestaContext` expõe `add`/`remove`/`toggle`/`clear` | ✅ |
| Cesta **sobrevive a refresh** (localStorage, client-only) | persist sem backend | `CestaContext.tsx`: init `useState(() => loadLinhas())` (read-through localStorage) + `useEffect` ⇒ `saveLinhas(linhas)` a cada mudança; corrompido/ausente ⇒ cesta vazia | ✅ |
| **Barra persistente** mostra o COMBINADO **com o drawer fechado** | total visível sempre | `Cesta.tsx:82` barra `fixed bottom-4 right-4` (largura = drawer, `max-w-sm`) renderizada sempre que **`cesta.size > 0`** — **independe de `open`** (`size===0 ⇒ return null`); l.95–102 mostram `N linha(s) · {impressões/dia} · {%AB}` | ✅ |
| COMBINADO **recalcula** a cada ＋/remover | recompute on change | `CestaContext` `useEffect` na **chave das ids** ⇒ `POST /api/lines/aggregate` (stateless); cesta vazia ⇒ sem combinado; campos `impressions.util/sab/dom`, `pctAbPonderado`, `faixaIndicativaPct` | ✅ |
| **Drawer** (aberto) mostra **per-linha + honestidade** | detalhe sob demanda | `Cesta.tsx:121` shadcn `Sheet` → header `Cesta · N linhas`, stats `util/sab/dom`, lista per-linha (com remover) + nota de honestidade (OTS somado, ±N%) | ✅ |
| Botão **exportar** chama a **`ProposalPort`** | gancho EP4-07 | `Cesta.tsx:67` `await port.export(payload)` via `useProposalPort()`; adapter **local F1** (PDF client-side, stub EP4-07) com guarda `EmptyCestaError` | ✅ |
| **Reuso** (sem dependência nova) | shadcn + `intelClient`/`useAggregate` já no spark | `Sheet`/`table`/`badge`/`button` de shadcn existentes; `/aggregate` via `intelClient` do EP4-01 — nenhuma lib nova | ✅ |

## Build / testes (escopo declarado)

| Gate | Comando | Resultado | Veredito |
|---|---|---|---|
| Suíte cheia | `npx vitest run` | **119/119** (0 falhas, 0 skips) — **+13** vs **106/106** do EP4-05 | ✅ |
| Testes novos (EP4-06) | `vitest` (recorte) | **13/13** — `CestaContext` (persist/refresh, add/remove/toggle/clear, recompute do combinado) + `Cesta` (barra com drawer fechado, %AB/impressões, drawer per-linha, export) + `ProposalPort` (adapter local, `EmptyCestaError`) (13 criados = 13 esperados) | ✅ |
| Build | `npm run build` (vite build) | **OK** — 2.565 módulos, ~3,1 s, `dist/` gerado; aviso de chunk >500 kB **pré-existente** (`CorridorMap`/maplibre do EP4-04), **não** desta task | ✅ |
| Verificação dirigida (retry) | `vitest run src/modules/ooh/api/intelClient.test.tsx` | **9/9** — incluindo **os 4 antes vermelhos** | ✅ |
| DB | MCP `postgres-ooh` | **vazio por design** (`db.ok = true`, sem checks) — tarefa de front | ✅ |

- **Retry de toolchain (não de escopo):** os 4 vermelhos da 1ª passada eram do `intelClient`, **não** da
  cesta. `http.ts` lê `import.meta.env.VITE_INTEL_BASE_URL ?? ""`; o `.env.local` da máquina (presente,
  `=http://localhost:8087`) é **auto-carregado** pelo Vite/Vitest e **vazava** a base de dev, prefixando as
  URLs **relativas** que os testes asseriam. Fix em **um arquivo só** — `vitest.config.ts` `test.env:
  { VITE_INTEL_BASE_URL: "" }` — força base vazia (⇒ caminhos relativos) **determinística**, independente da
  máquina. Bug de **ambiente de teste**, não de produto.
- **Toolchain:** runner via `npx vitest` (**fallback** — `bun` ausente no ambiente). Pendência operacional
  **viva desde EP3-01/EP4-01/EP4-04/EP4-05**: padronizar o runner (instalar `bun` ou fixar `vitest` no CI) p/
  não depender de `npx`/`npm` ad-hoc — e o leak do `.env.local` reforça: **fixar o env de teste no CI**.

## Decisões / desvios

- **Cesta = barra persistente + drawer (decisão da task confirmada):** a **barra fixa** mostra o COMBINADO o
  tempo todo (o planejador vê o total correr enquanto adiciona linhas), o **drawer** (`Sheet`) abre o per-linha
  + exportar sob demanda. Resolve o dilema drawer-vs-painel — melhor dos dois. *(Alternativas descartadas:
  só-drawer ⇒ total some quando fecha; só-painel ⇒ rouba largura permanente da tela.)*
- **Estado client-only com persist em `localStorage` (sem backend):** a cesta é **efêmera** mas **sobrevive a
  refresh** — mesma postura "client-only" do módulo. Sem tabela, sem `tenant_id`, sem round-trip de salvar.
  *(Trade-off aceito: cesta não cruza dispositivos/sessões — fora do escopo F1; reabrir pós-cutover se virar
  requisito.)*
- **`ProposalPort` como porta (hexagonal no front):** a tela depende **só** da interface `ProposalPort`
  (`export(cesta): Promise<void>`), nunca da implementação. F1 = **adapter local** (PDF client-side, stub do
  EP4-07, sem persistência); o **ponto de troca** pra um adapter remoto pós-F1 já está isolado. Guarda
  `EmptyCestaError` (cesta vazia ⇒ não exporta).
- **COMBINADO via `/aggregate` stateless (não recalculado no front):** o combinado vem do **EP2-05** (`POST
  /api/lines/aggregate`) a cada ＋/remover; o front **não** soma impressão nem pondera %AB — consome o número
  resolvido. Mantém a **mesma `CestaSelection`** que `RankingList`/`LineFicha` já consomem ⇒ telas **intactas**.
- **Retry/fix do `.env.local`:** desvio de **toolchain**, registrado acima — único arquivo tocado no retry.

## Honestidade do COMBINADO (EP2-05 / `types.ts`)

- COMBINADO = **OTS somado**, **não alcance único** — sobreposições de público **não** descontadas; **sem
  "score combinado"** (score é por-linha, não agrega). Faixa indicativa **±35%** (default; valor real em
  `combined.impressions.faixaIndicativaPct`, carimbado pelo `/aggregate`). A nota de honestidade aparece no
  **drawer**, junto do per-linha — coerente com o EP4-08 (honestidade de UI).

## Contract-check back↔front (review)

- **Cesta → `/aggregate` (EP2-05):** o COMBINADO mapeia 1:1 o payload do `POST /api/lines/aggregate` do
  `uai-ooh-intel` — `impressions.util/sab/dom`, `pctAbPonderado`, `faixaIndicativaPct`. **Stateless**: o back
  recebe a lista de ids e devolve o agregado; **nenhum estado de cesta** no servidor. Nenhum campo novo pedido.
- **`useAggregate`/`intelClient` (EP4-01):** a chamada reusa o cliente já existente; mesma camada `http.ts`
  cujo env (`VITE_INTEL_BASE_URL`) foi o que vazou nos testes — daí o fix no `vitest.config.ts`.
- **Fronteira interna `ProposalPort`:** o export **não** chama o back nesta task — é porta com **adapter
  local** (PDF client-side, EP4-07). O contrato (`ProposalPort.export(cesta)`) é o ponto onde o EP4-07 pluga.
- **Contrato de consumo preservado:** `CestaContext` estende `add`/`remove`/`clear`/COMBINADO **sem** quebrar a
  `CestaSelection` (`isInCesta`/`toggle`/`size`) — por isso `RankingList`/`LineFicha` não mudaram de assinatura.

## Estado do `dataset_version`

- **N/A — tarefa de front.** O `uai-portal` é o shell e **não acessa o banco `ooh`** nesta task; a validação de
  DB veio **vazia por design** (`db.ok = true`, sem checks). O COMBINADO chega ao front **via API do
  `uai-ooh-intel`** (`/aggregate`, consumidor read-only do `serving` ACTIVE). Nenhum ciclo de versão é tocado
  aqui — promoção do `dataset_version` segue pendência do EP1/serving, não daqui. Os valores reais (impressões
  somadas, %AB ponderado, faixa) só preenchem a barra/drawer com o intel no ar (EP2-05/EP2-09/deploy).

## Adversarial — o que o cético tentou (refuted: false)

O vetor foi confrontado contra a **working tree atual** do `uai-portal`; **não derrubou** o done.

- **Vetor 1 (GATE/critério) — "os 3 critérios não estão cumpridos no código real" (add/remove lista+ficha,
  persist localStorage, barra com COMBINADO visível com drawer fechado + recálculo, export → `ProposalPort`).**
  **Não derrubou:** **(a)** add/remove de **ambas** as telas — `RankingList.tsx:140` e `LineFicha.tsx:96`
  chamam `cesta.toggle`; `CestaContext` expõe `add`/`remove`/`toggle`/`clear`. **(b)** **persistência** —
  `CestaContext` inicializa de `loadLinhas()` e grava via `saveLinhas()` no `useEffect` (sobrevive a refresh,
  zero backend). **(c)** **barra sempre visível** — `Cesta.tsx:81–118` renderiza a barra `fixed` sempre que
  `size > 0`, **independente de `open`**, com `impressões/dia · %AB`, recálculo via `useEffect` na chave das ids
  ⇒ `/aggregate`. **(d)** **export** — `port.export(payload)` via `useProposalPort()` (adapter local F1).
  Reforço: `npx vitest run` **119/119 AGORA**, dos quais **13/13** de cesta.
- **Único ponto que o cético chegou a derrubar (na 1ª passada) — os 4 testes vermelhos do `intelClient`.**
  Diagnosticado como **vazamento do `.env.local`** (base de dev `http://localhost:8087` prefixando URLs
  relativas), **não** bug da cesta; corrigido determinístico em `vitest.config.ts` (`test.env.VITE_INTEL_BASE_URL
  = ""`). Re-run: `intelClient` **9/9**, suíte **119/119**. Review confirmou causa-raiz e aprovou o retry.
- **Limite reconhecido (não derruba):** os valores reais do COMBINADO (impressões somadas, %AB ponderado,
  faixa) só aparecem com o `uai-ooh-intel` no ar (EP2-05/EP2-09/deploy); o que está provado é **estado +
  persist + wiring + trigger de recálculo + gancho de export** por código e teste — não um render contra
  `/aggregate` servido ao vivo. Idem o **PDF**: a `ProposalPort` está plugada, o arquivo real chega no EP4-07.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/epicos/runs/EP4-06-cesta.md`).
- Código no repo-alvo: `uai-portal` (ex-`uai-spark`) @ `feat/ooh-ep4-06` — módulo `/ooh`:
  `src/modules/ooh/cesta/CestaContext.tsx` (estado client + persist `localStorage` + COMBINADO via
  `/aggregate`), `src/modules/ooh/cesta/Cesta.tsx` (barra `fixed` + drawer `Sheet`, export),
  `src/modules/ooh/proposal/ProposalPort.ts` (porta + adapter local F1 + `EmptyCestaError`),
  `src/modules/ooh/hooks/useCestaSelection.ts`; pontos de add/remove em `RankingList.tsx`/`LineFicha.tsx`;
  fix de env em `vitest.config.ts`. Testes `CestaContext` + `Cesta` + `ProposalPort` (**13** novos).
- Próximo: **EP4-07** (export PDF — pluga o adapter real na `ProposalPort` já no lugar), **EP4-08**
  (honestidade de UI), **EP4-09** (mapa da cesta). Pendência operacional carregada: **padronizar o runner +
  fixar o env de teste no CI** (`bun`/vitest, `VITE_INTEL_BASE_URL`) p/ não depender de `npx`/`npm` ad-hoc nem
  do `.env.local` da máquina.
</content>
</invoke>
