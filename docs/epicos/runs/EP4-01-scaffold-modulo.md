# Run — EP4-01 (front: scaffold do módulo /ooh no shell) · 2026-06-06 · ✅ DONE

> Execução no `uai-portal` (ex-`uai-spark`, branch `feat/ooh-ep4-01`), plugando o **módulo OOH como
> rota dentro do shell** (EP3-01) e montando a costura de consumo do intel: `intelClient` (react-query),
> contrato TS (`types.ts`, espelho dos records do `uai-ooh-intel`) e a `ProposalPort` (interface +
> adapter local F1). Stack React 18 · TS · Vite · `@tanstack/react-query`. Tarefa **só de front** —
> **não toca o banco `ooh`** (validação de DB vazia por design). Task do mapa F1:
> `docs/epicos/bloco1/f1/03-front/modulo-ooh/EP4-01-scaffold-modulo.md`.
>
> **Veredito:** o critério de pronto passa — o portal compila (`npm run build` ⇒ **1688 módulos** em
> ~1,2 s; `bun` ausente, caiu no `npm` como previsto pelo comando), o **typecheck extra** `npx tsc
> --noEmit -p tsconfig.app.json` fecha **exit 0** (o `vite build` não tipa por padrão, então rodei o
> `tsc` p/ garantir o contrato TS), a rota `/ooh` renderiza a **`PlanejamentoPage` (skeleton)** dentro
> do `ProtectedLayout`, o `useLines` aponta pro **intel direto com Bearer** e a `ProposalPort` está
> definida com adapter local stub. Review **aprovou** (13/13 testes do módulo, **43/43** na suíte cheia
> do portal, **sem regressão**); o **contract-check back↔front** contra os records reais do
> `uai-ooh-intel` bate **1:1**; vitest **43/43** (2 testes novos = 2 esperados); a rodada de refute
> **não derrubou** o done. → **DONE**. Destrava EP4-02..09 (todas usam o `intelClient` + a `ProposalPort`).

## Critério de pronto vs. medido (working tree `uai-portal` @ `feat/ooh-ep4-01`)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| Módulo `/ooh` renderiza "Planejamento OOH" (skeleton) no shell | rota dentro do shell+guard | `App.tsx:19` mapeia `{path:'/ooh', element:<PlanejamentoPage/>}` sob `<ProtectedLayout>`; `PlanejamentoPage.tsx:20` tem `<h1>Planejamento OOH</h1>` | ✅ |
| Hook do intel (`useLines`) busca e mostra o **count (303)** | aponta pro **intel direto** com Bearer | `intelClient.ts`: `useLines` via `fetch`+react-query c/ `VITE_INTEL_URL` + `Authorization: Bearer` do `AuthContext`. **303** = alvo de contrato (core.line_metrics, épico 3) — wiring verificado por código+teste; **fetch ao vivo não exercitado** (front-only, intel fora do escopo) | ✅¹ |
| `ProposalPort` definida + adapter local stub | interface TS + adapter F1 | `proposal/ProposalPort.ts`: `interface ProposalPort { export(cesta: Cesta): Promise<void> }` + adapter local stub (F1 = PDF client-side; Bloco 3 = save→cms) | ✅ |
| Portal compila | build de produção OK | `npm run build` (`vite build`) ⇒ **1688 módulos** transformados em ~1,2 s; `dist/` gerado | ✅ |
| Contrato TS tipa | sem erros de tipo | `npx tsc --noEmit -p tsconfig.app.json` ⇒ **exit 0** | ✅ |

> ¹ Único ponto com asterisco: o **303** é o número de contrato (total de linhas em `core.line_metrics`,
> validado no épico 3), não um count retornado por uma chamada HTTP ao vivo nesta run. O que está provado
> aqui é o **wiring** (`useLines` → intel direto + Bearer) por código e teste; o número real só aparece
> com o intel no ar (EP2-09/deploy). Não derruba o done do scaffold, mas fica carimbado.

## Build / testes (escopo declarado)

- **Build:** `npm run build` (`vite build`) ⇒ **BUILD SUCCESS**, **1688 módulos** transformados em ~1,2 s.
  Fallback p/ `npm` **previsto pelo comando** (`bun` não instalado no ambiente).
- **Typecheck:** `npx tsc --noEmit -p tsconfig.app.json` ⇒ **exit 0** (sem erros). Rodado **à parte** de
  propósito: o `vite build` não faz typecheck, então o `tsc` garante o contrato TS do `types.ts`.
- **Testes:** `npx vitest run` (fallback — `bun` ausente; `package.json` `"test"` = `vitest run`).
  **43/43** na suíte completa do portal (0 falhas, 0 skips), **13/13** no recorte do módulo `/ooh`.
  **2 testes novos criados = 2 esperados**. **Sem regressão** no portal legado (marketing).

## Decisões / desvios

- **Fetcher = `fetch` + react-query** (conforme spec): react-query já existe no portal, `fetch` nativo no
  fetcher, **zero dependência nova**. Axios descartado (interceptors prontos, mas +1 dep p/ ganho pequeno).
- **`types.ts` como espelho dos records do `uai-ooh-intel`** — arquivo extra além da estrutura mínima do
  spec, criado p/ dar um **contrato TS único** ao módulo (consumido por todos os hooks do `intelClient`).
  É a âncora do contract-check abaixo.
- **`intelClient` aponta pro intel DIRETO** (não via `uai-cms`) — revisão consciente da regra BFF
  (PRD §7): o módulo OOH lê o `uai-ooh-intel` direto com Bearer. Decisão herdada do PRD, não desvio.
- **Toolchain de teste — `bun` ausente** (carregada desde EP3-01): a suíte roda via `npx vitest`/`npm`
  como fallback. Pendência operacional viva: **padronizar o runner** (instalar `bun` ou fixar `vitest`
  no CI) p/ não depender de `npx`/`npm` ad-hoc.
- **Suíte de testes do estágio Test rodada; estágios além (deploy) fora de escopo** — scaffold só.

## Contract-check back↔front (review)

Conferido **1:1** contra os records reais do `uai-ooh-intel` — **bate**:

- **Endpoints + query params** do `intelClient` casam com os controllers do intel.
- **Campos camelCase** de `Line`, `LineRanking`, `Region`, `LineMetrics` (e nested), `Combined`/
  `AggregateResult`, `ShapeResponse`/`StopRef`, `AggregateRequest` — todos batem.
- **Back sem `PropertyNamingStrategy` snake_case** → o JSON sai camelCase, então o `types.ts` espelha o
  contrato sem tradução. Nenhum descasamento de nome.

## Estado do `dataset_version`

- **N/A — tarefa de front.** O `uai-portal` é o shell e **não acessa o banco `ooh`** nesta task; a
  validação de DB veio **vazia por design** (`db.ok=true`, sem checks). Os dados de OOH chegam ao front
  **via API do `uai-ooh-intel`** (consumidor read-only do `serving` ACTIVE) — nenhum ciclo de versão é
  tocado aqui. O `dataset_version` segue como estava (pendência de promoção é do EP1/serving, não daqui).

## Adversarial — o que o cético tentou (refuted: false)

Os vetores foram confrontados contra a **working tree atual** do `uai-portal`; **nenhum derrubou** o done.

- **Vetor 1 (critério 1) — "a rota `/ooh` não renderiza 'Planejamento OOH' no shell".** **Não derrubou:**
  `App.tsx:19` mapeia `{path:'/ooh', element:<PlanejamentoPage/>}` **dentro do `<ProtectedLayout>`**
  (shell+guard) e `PlanejamentoPage.tsx:20` tem `<h1>Planejamento OOH</h1>`. Testes verdes confirmam:
  *"ProtectedLayout — guarda da rota /ooh"* e *"PortalSidebar … Planejamento OOH apontando para /ooh"*.
  Render no shell: **confirmado**.
- **Vetor 2 (critério 2) — "o `useLines` não aponta pro intel direto / sem Bearer".** **Não derrubou:**
  o `intelClient.ts` resolve a base por `VITE_INTEL_URL` (**intel direto**, não cms) e injeta
  `Authorization: Bearer` a partir do `AuthContext` (EP3-02). Wiring: **legítimo**. Limite reconhecido
  (não derruba): o **303** é alvo de contrato, não count de chamada ao vivo — depende do intel no ar.
- **Reforço empírico:** vitest **43/43 AGORA** (13/13 no módulo), `tsc --noEmit` **exit 0** e
  `vite build` **OK (1688 módulos)** — asseverando o render protegido, o wiring do hook e o contrato TS.
  Contract-check back↔front **1:1**. Sem regressão no portal legado.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/epicos/runs/EP4-01-scaffold-modulo.md`).
- Código no repo-alvo: `uai-portal` (ex-`uai-spark`) @ `feat/ooh-ep4-01` —
  `src/modules/ooh/{pages,api,proposal}/` (`PlanejamentoPage`, `intelClient.ts`, `types.ts`, `ProposalPort.ts`).
- Próximo: **EP4-02** (lista + filtros) e demais EP4-03..09 — todas consomem o `intelClient` + a
  `ProposalPort` desta base. Pendência operacional carregada: **padronizar o runner de testes**
  (`bun`/vitest) p/ não depender de `npx`/`npm` ad-hoc.
