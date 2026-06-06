# Run — EP3-02 (plataforma: login SSO — substitui o mock) · 2026-06-05 · ⚠️ PARCIAL / 🚧 STUB

> Execução no `uai-portal` (ex-`uai-spark`, branch `feat/ooh-ep3-02`), trocando o **`AuthContext` mock
> da EP3-01** por **auth real em modo STUB**: `AuthContext` de verdade (token+usuário+role+logout),
> guard de rota reusado e **interceptor Bearer** propagando o token pro intel. Stack React 18 · TS ·
> Vite · Tailwind · shadcn/ui. Tarefa **só de front** — **não toca o banco `ooh`** (validação de DB
> vazia por design, igual EP3-01). Task do mapa F1: `docs/epicos/bloco1/f1/03-front/shell/EP3-02-login-sso.md`.
>
> **Veredito:** o **critério de pronto F1 (stub) passa por inteiro** — o mock saiu do código de produção
> (prod usa a chave `uai_session` em `src/lib/auth/tokenStore.ts`; `uai_auth`/`uai_role`/`uai_user` só
> sobrevivem como asserts de teste provando que ficaram `null` + um comentário documental), o
> `AuthContext` real espelha o `tokenStore` (fonte única fora do React) via `subscribe`, o login local
> (`stubLogin` em `src/lib/auth/stubProvider.ts`) persiste um token com **shape de JWT**, o guard
> `ProtectedLayout` reusa `isAuthenticated` e o `apiFetch` (`src/lib/api/http.ts`) injeta o header
> `Authorization: Bearer`. Build OK (`vite build`, **1685 módulos**), `tsc --noEmit` strict **EXIT 0**,
> review **APROVADO**, testes **verdes** e a rodada de `refute` **não derrubou** nenhum vetor (`refuted:false`).
> **Mas a task NÃO é DONE:** o critério **Final** (SSO redirect real + token do `uai-auth`) é
> insatisfazível agora — `uai-auth` é **repo vazio (epic-002), gate RED**. Logo a orquestração a mantém
> em **`partial`** → carimbo **PARCIAL / STUB (fechamento real deferido)**. Mesmo bloqueio upstream da EP2-07.

## Critério de pronto vs. medido (working tree `uai-portal` @ `feat/ooh-ep3-02`)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| **F1** mock `localStorage` fake do spark **sai** do código de produção | 0 leitura de `uai_auth`/`uai_role`/`uai_user` em prod | prod usa a chave **`uai_session`** (`src/lib/auth/tokenStore.ts`); chaves antigas só em **asserts de teste** (verificando `=== null`) + 1 comentário documental | ✅ |
| **F1** `AuthContext` real (token + usuário + role + logout) | contexto real, não fake | `src/contexts/AuthContext.tsx` entrega token+usuário+role+logout, **espelhando o `tokenStore`** (fonte única fora do React) via `subscribe` | ✅ |
| **F1** login local guarda o token | token persistido com estrutura real | `stubLogin` (`src/lib/auth/stubProvider.ts`) → token com **shape de JWT** persistido no `tokenStore` | ✅ |
| **F1** rotas protegidas | guard real reusado | `ProtectedLayout` reusa `isAuthenticated` (derivado do `tokenStore`), não hardcoded | ✅ |
| **F1** **Bearer** propagado pro intel | header `Authorization` injetado no client de API | `apiFetch` (`src/lib/api/http.ts`) injeta `Authorization: Bearer <token>` | ✅ |
| **F1** logout | limpa sessão | `AuthContext.logout` zera o `tokenStore` → `isAuthenticated` cai | ✅ |
| Compila (build de produção) | EXIT 0 | `bun run build` indisponível (**`bun` ausente**, igual EP3-01) → fallback `npm run build` (`vite build`) **OK, 1685 módulos** (baseline EP3-01 = 1682; **+3** das libs novas), `dist/` gerado. `npx tsc --noEmit -p tsconfig.app.json` (strict) **EXIT 0** | ✅ |
| Suíte de testes | verde | full run **30/30** (0 fail / 0 skip); validação dirigida nos **5 arquivos tocados** = **26/26** (24 casos novos + 2 da EP3-01 ajustados); **5 arquivos de teste novos = 5 esperados** | ✅ |
| **Final (pós-`uai-auth`)** SSO redirect real + token do `uai-auth` | "Entrar com uAI" → redirect → callback com token real | **NÃO implementável:** `uai-auth` é **repo vazio (epic-002), gate RED** — sem endpoint de SSO/token. Roda em **stub** (login local) | ⚠️ DEFERIDO (BLOQUEADO upstream) |

## Build / testes (escopo declarado)

- **Build:** `bun run build` indisponível (**`bun` não instalado** no ambiente, mesmo desvio da EP3-01) ⇒
  fallback `npm run build` (`vite build`): **OK, 1685 módulos transformados**, `dist/` gerado. O delta de
  **+3 módulos** sobre o baseline EP3-01 (1682) vem das **libs novas** de auth. Typecheck extra
  `npx tsc --noEmit -p tsconfig.app.json` (strict) ⇒ **EXIT 0, sem erros**.
- **Testes:** `npx vitest run` (**fallback**, `bun` ausente). **Full run 30/30** (0 fail / 0 skip).
  A **validação dirigida** do implement (NÃO a suíte completa) rodou os **5 arquivos tocados** ⇒ **26/26**
  (**24 casos novos** + **2 da EP3-01 ajustados**). Contagem do estágio Test: **5 arquivos de teste novos
  criados = 5 esperados**. Os warnings de **React Router future flags v7** são **pré-existentes** (herdados
  da EP3-01) e **não quebram** build nem testes.

## Decisões / desvios

- **Mock da EP3-01 erradicado da produção.** A EP3-01 deixou o guard/sidebar lendo `uai_auth` direto do
  `localStorage`. A EP3-02 troca isso por um **`tokenStore` dedicado** (`src/lib/auth/tokenStore.ts`,
  chave **`uai_session`**) como **fonte única fora do React**; o `AuthContext` apenas **espelha** o store
  via `subscribe`. As chaves antigas (`uai_auth`/`uai_role`/`uai_user`) **não são mais lidas/escritas em
  prod** — só restam como **asserts de teste** garantindo que ficaram `null` (regressão) + 1 comentário.
- **Login em modo STUB, mas com a estrutura real (troca barata depois).** `stubLogin`
  (`src/lib/auth/stubProvider.ts`) emite um token com **shape de JWT** e o persiste. O que **muda** quando
  `uai-auth` subir é só a **origem do token**: `stubLogin` → **SSO redirect** real (Decisão B da task,
  2026-06-05). `AuthContext`, guard e interceptor Bearer **já são definitivos**.
- **Interceptor Bearer no client de API.** `apiFetch` (`src/lib/api/http.ts`) injeta
  `Authorization: Bearer`. Casa com a **EP2-07** (intel valida por introspection) — ambos em stub hoje;
  destravam juntos quando `uai-auth` existir.
- **Casa com EP2-07 (par front↔back de auth).** EP3-02 é **quem obtém** o token; EP2-07 é **quem valida**.
  As duas ficam `partial`/stub pelo **mesmo** bloqueio (`uai-auth` = epic-002, repo vazio).
- **Toolchain de teste — `bun` ausente (desvio carregado desde EP3-01).** Build e testes rodam via
  `npm`/`npx` como fallback. Pendência **padronizar o runner** (instalar `bun` ou fixar `vitest`+`vite` no
  `package.json`/CI) segue aberta — não afeta o veredito desta task.

## Estado do `dataset_version`

- **N/A — tarefa de front.** O `uai-portal` é o shell da plataforma e **não acessa o banco `ooh`** nesta
  task; a validação de DB veio **vazia por design** (`db.ok=true`, **0 checks**). Auth no front é estado de
  sessão no browser (`tokenStore`/`localStorage`), **não materializa linha** no `ooh` nem toca o ciclo de
  versões do normalizer (EP1). Os dados de OOH só chegam ao front **via API do `uai-ooh-intel`** (read-only
  do `serving` ACTIVE) a partir da **EP4**. ✅ (inalterado)

## Adversarial — o que o cético tentou (refuted: **false**)

A rodada de `refute` **não derrubou** o critério **F1 (stub)** — todos os vetores bateram contra a working
tree atual do `uai-portal` e se sustentaram. O único ponto em que o cético tem razão é o **Final** (SSO
real), e é exatamente **por isso** que o status é **PARCIAL**, não porque o refute quebrou counts.

- **Vetor 1 — "o mock antigo continua no código de produção".** **Não derrubou:** o código de produção usa
  a chave **`uai_session`** (`src/lib/auth/tokenStore.ts`). As únicas referências a
  `uai_auth`/`uai_role`/`uai_user` são **asserts de teste** verificando que são `null` + um comentário
  documental. Mock: **fora da produção**.
- **Vetor 2 — "o `AuthContext` ainda é fake".** **Não derrubou:** `src/contexts/AuthContext.tsx` entrega
  **token + usuário + role + logout**, espelhando o `tokenStore` (fonte única fora do React) via
  `subscribe`. Contexto: **real**.
- **Vetor 3 — "o Bearer não chega no intel".** **Não derrubou:** `apiFetch` (`src/lib/api/http.ts`) injeta
  `Authorization: Bearer <token>` nas chamadas. Propagação: **confere**.
- **Vetor 4 — "o login stub não guarda token de verdade / rota não protege".** **Não derrubou:** `stubLogin`
  (`src/lib/auth/stubProvider.ts`) persiste um token com **shape de JWT**, `ProtectedLayout` reusa
  `isAuthenticated`, e o `logout` zera a sessão. Estrutura: **real**.
- **Reforço empírico:** **30/30** no full run e **26/26** na validação dirigida dos 5 arquivos tocados
  (24 casos novos), `vite build` **OK** (1685 módulos) e `tsc --noEmit` strict **EXIT 0**.
- **O que o cético acerta (e vira o carimbo PARCIAL):** o critério **Final** — **SSO redirect real + token
  do `uai-auth`** — é **inimplementável agora**: `uai-auth` é **repo vazio (epic-002), gate RED**. O stub
  (login local) **não** é o SSO. Equiparar stub a final mascararia o trabalho de integração que ainda falta.
  → DoD completo **insatisfazível**; a task fica **`partial`/STUB**. (Mesmo padrão de bloqueio upstream da EP2-07.)

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`uai-ooh-pm/docs/epicos/runs/EP3-02-login-sso.md`).
- Código no repo-alvo: `uai-portal` (ex-`uai-spark`) @ `feat/ooh-ep3-02` (stub do F1; build OK; testes verdes).
- **Status:** **PARCIAL / STUB** — orquestração mantém em **`partial`** (fechamento real deferido).
- **Destrava com:** `uai-auth` subir o fluxo SSO + emissão de token (epic-002) → trocar `stubLogin` →
  **SSO redirect** real + token do `uai-auth` → **re-rodar `only:['EP3-02']`** (reconcilia `partial` → `done`).
  No mesmo gatilho, **EP2-07** liga a introspection real — o par front↔back de auth fecha junto.
- **Relaciona:** **EP3-01** (shell + guard de rota que esta task passa a alimentar com o `tokenStore` real),
  **EP2-07** (intel valida o Bearer propagado aqui), **EP4** (client de API do módulo OOH que herda o
  interceptor Bearer). Pendência operacional carregada: **padronizar o runner de testes** (`bun`/vitest/vite).
