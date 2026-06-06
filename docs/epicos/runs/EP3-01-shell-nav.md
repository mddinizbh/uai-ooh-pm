# Run — EP3-01 (plataforma: shell + nav de verticais) · 2026-06-05 · ✅ DONE

> Execução no `uai-portal` (ex-`uai-spark`, branch `feat/ooh-ep3-01`), adaptando o **spark como shell
> da plataforma** e habilitando a **nav de verticais com OOH como 1º vertical**. Stack React 18 · TS ·
> Vite · Tailwind · shadcn/ui. Tarefa **só de front** — **não toca o banco `ooh`** (validação de DB
> vazia por design). Task do mapa F1: `docs/epicos/bloco1/f1/03-front/shell/EP3-01-shell-nav.md`.
>
> **Veredito:** o critério de pronto passa — o shell compila (`vite build` OK, 1682 módulos), a
> `PortalSidebar` reestruturada em verticais expõe **OOH como `verticals[0]`** (item "Planejamento
> OOH" → `/ooh`) preservando o marketing legado, a rota `/ooh` fica **protegida pelo `ProtectedLayout`
> com guard real** (`isAuthenticated` derivado de `localStorage uai_auth`, não hardcoded) e o UI kit/tema
> shadcn está completo. O item **MAJOR** do review anterior (polyfill de `localStorage` p/ os testes sob
> jsdom no Node 26) foi resolvido. Review aprovou; vitest **6/6** (5 novos = 5 esperados); a rodada de
> refute **não derrubou** o done. → **DONE**. Destrava EP4 (módulo OOH mora neste shell).

## Critério de pronto vs. medido (working tree `uai-portal` @ `feat/ooh-ep3-01`)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| Shell compila | build de produção OK | `npx vite build` ⇒ OK, **1682 módulos**, `dist/` gerado | ✅ |
| Nav mostra o **vertical OOH** (1º vertical) | OOH em 1º, marketing legado preservado | `PortalSidebar.tsx` `verticals[0].id==='ooh'`; item **"Planejamento OOH" → `/ooh`** (`data-vertical`); nav de marketing (Vivian/campanhas) mantida | ✅ |
| Rota `/ooh` **protegida** | guard real de auth, não hardcoded | `/ooh` sob `ProtectedLayout` (`App.tsx:19,44`); `isAuthenticated` **derivado de `localStorage uai_auth`** (`AuthContext.tsx:19`) | ✅ |
| UI kit/tema OK | shadcn/ui + branding uAI | UI kit/tema shadcn **completo** (reusado do spark) | ✅ |

## Build / testes (escopo declarado)

- **Testes:** `npx vitest run` — **fallback** porque **`bun` não está instalado** no ambiente.
  **6/6 passou** (0 falhas, 0 skips): `PortalSidebar.test.tsx` (3) + `ProtectedLayout.test.tsx` (2) +
  `example.test.ts` (1). **5 testes novos criados = 5 esperados** (os 2 arquivos novos).
- **Build:** `npx vite build` ⇒ OK (1682 módulos, `dist/` gerado) — exercitado também na rodada de refute.

## Decisões / desvios

- **`uai-spark` promovido a `uai-portal` (decisão 2026-06-05).** O spark vira o **shell oficial** da
  plataforma; o módulo OOH (EP4) pluga como rota. Trabalho na branch `feat/ooh-ep3-01`.
- **MAJOR resolvido — polyfill de `localStorage` em `src/test/setup.ts`.** Causa-raiz: os 5 testes novos
  rodam sob **jsdom no Node 26**, que não fornece `localStorage`, e tanto o guard de auth quanto a
  sidebar leem `uai_auth` do storage. Correção: implementação **in-memory de `Storage`** com a interface
  completa (`getItem`/`setItem`/`removeItem`/`clear`/`length`/`key`), instalada em **`globalThis` e
  `window`** (`configurable`+`writable`), com **store único compartilhado** (logo `localStorage.clear()`
  nos hooks de teste realmente zera). Sem essa peça os 5 testes não passavam.
- **`PortalSidebar` reestruturada p/ verticais.** Passou de nav monolítica de marketing p/ modelo de
  **verticais** (exports `NavItem`/`NavGroup`/`Vertical`, atributo `data-vertical`), com OOH em 1º.
- **`/ooh` ainda é placeholder (`OohPlanning`).** A rota existe e está protegida, mas a página é stub —
  **sem contrato de endpoint** nesta task; o conteúdo real do módulo OOH é da **EP4** (consumindo os
  endpoints da EP2). Coerente com o escopo "shell + nav".
- **Toolchain de teste — `bun` ausente.** A suite roda via `npx vitest` como fallback; vale **padronizar
  o runner** (instalar `bun` ou fixar `vitest` no `package.json`/CI) p/ não depender do `npx` ad-hoc.

## Estado do `dataset_version`

- **N/A — tarefa de front.** O `uai-portal` é o shell da plataforma e **não acessa o banco `ooh`** nesta
  task; a validação de DB veio **vazia por design** (`db.ok=true`, sem checks). Os dados de OOH chegam ao
  front **via API do `uai-ooh-intel`** (consumidor read-only do `serving` ACTIVE) só a partir da EP4 —
  nenhum ciclo de versão é tocado aqui.

## Adversarial — o que o cético tentou (refuted: false)

Os 3 vetores foram confrontados contra a **working tree atual** do `uai-portal`; **nenhum derrubou** o done.

- **Vetor 1 — "o shell nem compila".** **Não derrubou:** `npx vite build` conclui OK (**1682 módulos**,
  `dist/` gerado). Shell: **compila**.
- **Vetor 2 — "a nav não mostra OOH como 1º vertical / quebrou o marketing".** **Não derrubou:**
  `PortalSidebar.tsx` tem `verticals[0].id==='ooh'` com o item **"Planejamento OOH" → `/ooh`**, e a nav
  legada de marketing **segue presente**. Nav: **confere**.
- **Vetor 3 — "a proteção de `/ooh` é fake / guard hardcoded".** **Não derrubou:** `/ooh` está sob
  `ProtectedLayout` (`App.tsx:19,44`) e o `isAuthenticated` é **derivado de `localStorage uai_auth`**
  (`AuthContext.tsx:19`) — guard **real**, não constante. Proteção: **legítima**.
- **Reforço empírico:** vitest **6/6 AGORA**, asseverando exatamente esses critérios (sidebar com OOH 1º
  + guard de rota). O UI kit/tema shadcn está completo. Único limite reconhecido (não derruba o done):
  `/ooh` é **placeholder sem contrato de endpoint** — conteúdo real fica pra **EP4**.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`uai-ooh-pm/docs/epicos/runs/EP3-01-shell-nav.md`).
- Código no repo-alvo: `uai-portal` (ex-`uai-spark`) @ `feat/ooh-ep3-01`.
- Próximo: **EP4** (módulo OOH como rota neste shell, consumindo a API do `uai-ooh-intel`) e **EP3-03**
  (deploy do shell). Pendência operacional carregada: **padronizar o runner de testes** (`bun`/vitest)
  pra não depender de `npx` ad-hoc.
