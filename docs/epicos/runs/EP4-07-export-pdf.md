# Run — EP4-07 (front: export PDF · "modo proposta" client-side) · 2026-06-06 · ✅ DONE

> Execução no `uai-portal` (ex-`uai-spark`, branch `feat/ooh-ep4-07`), entregando o **export PDF** — o
> **"modo proposta"** que fecha o workflow **explora → cesta → export**. O botão **exportar** (já costurado no
> **EP4-06** em `Cesta.tsx` → `handleExport` → `port.export(payload)`, antes mirando um **stub**) agora aciona a
> **geração real**: `buildProposalHtml` monta a **capa** (cliente · **BH Bus Mídia** · data) + **ficha resumida
> por linha** + **COMBINADO** + **🗺️ mapa da cesta** (snapshot PNG do **EP4-09**, se houver) + **disclaimer de
> honestidade**, e `printProposal` abre o **diálogo de impressão** (salvar como PDF). A saída sai **100%
> client-side** por trás da **`ProposalPort`** (porta hexagonal do front, EP4-01): esta task **pluga o adapter
> local F1 real** no ponto que EP4-06/EP4-09 já tinham deixado isolado — **sem backend, sem persistência, sem
> dep nova** (segue a decisão da task: **modo proposta + print**, não `html2pdf`/`jspdf`). A arquitetura separa
> **função pura** (`buildProposalHtml`, monta a string HTML) de **efeito** (`printProposal`, render via
> **iframe** + `window.print`), e faz **escaping anti-injeção em todo texto dinâmico** (nome do cliente, nomes
> de linha). Stack React 18 · TS · shadcn/ui. Tarefa **só de front** — **não toca o banco `ooh`** (validação de
> DB vazia por design). Task do card: `docs/epicos/bloco1/f1/03-front/modulo-ooh/EP4-07-export-pdf.md`.
>
> **Veredito:** os **3 critérios de pronto** passam, com a suíte **reproduzida** (não só alegada): `npx vitest
> run` (fallback — `bun` ausente) ⇒ **152/152** (0 falhas, 0 skips), dos quais **16 novos do EP4-07**
> (`buildProposalHtml` + `printProposal` + adapter local + `Cesta`). Review **aprovou** (verdict APROVADO —
> escopo e repo-alvo corretos; critério atendido; qualidade alta: separação pura×efeito + escaping anti-injeção);
> o refute **não derrubou** o done. → **DONE**. Fecha o **modo proposta** do F1 e **encerra o workflow
> explora→cesta→export**; consome o `mapaSnapshot` que o EP4-09 já roteava pro payload.

## Critério de pronto vs. medido (working tree `uai-portal` @ `feat/ooh-ep4-07`)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| Botão **exportar** (da cesta) gera o PDF **"modo proposta"** (capa + linhas + COMBINADO + disclaimer) | botão → PDF imprimível com as 4 partes (+ mapa EP4-09) | `Cesta.tsx:269-278` botão `ooh-cesta-export` → `handleExport` (`:83`) → `port.export(payload)`; `buildProposalHtml` (`proposalDocument.ts:122`) monta **capa `.cover`** (cliente / BH Bus Mídia / data, `:212-221`), **`block--lines`** (ficha resumida por linha, `:223-228`), **`block--combined`** (`:230-233`) e o **mapa** via `mapaSnapshot` do EP4-09 (se houver) | ✅ |
| Via **`ProposalPort.export()`** (adapter local F1) — **client-side, sem backend** | porta hexagonal; adapter local; zero round-trip | `createLocalProposalAdapter` implementa `ProposalPort.export(cesta)`; gera HTML + imprime **no cliente**; **nenhuma** chamada de back nesta task; sem persistência, sem `tenant_id` | ✅ |
| **Disclaimer de honestidade SEMPRE presente** | impressões = estimativa ±35% / OTS (não pessoas únicas), ranking é o defensável, vigências — **incondicional** | `disclaimerHtml` **CONCATENADO INCONDICIONALMENTE** no retorno de `buildProposalHtml` (`:150-167`, `:235`) — não há ramo que o omita; coberto por teste dedicado | ✅ |
| **Sem dep nova** (modo proposta + print, decisão da task) | `window.print`/iframe, não lib de PDF | `printProposal` renderiza o HTML num **iframe** e dispara `window.print` (salvar como PDF); **nenhuma** lib `html2pdf`/`jspdf` adicionada | ✅ |
| **Segurança** do HTML gerado (texto dinâmico) | escaping anti-injeção | escaping aplicado a **todo** texto dinâmico (nome do cliente, nomes de linha) antes de entrar no HTML — destacado e validado no review | ✅ |

## Build / testes (escopo declarado)

| Gate | Comando | Resultado | Veredito |
|---|---|---|---|
| Suíte cheia | `npx vitest run` (fallback — `bun` ausente) | **152/152** (0 falhas, 0 skips) — **+16** novos do EP4-07 | ✅ |
| Testes novos (EP4-07) | `vitest` (recorte) | **16/16** (16 criados = 16 esperados) — `buildProposalHtml` (capa/linhas/COMBINADO/**disclaimer incondicional**/mapa opcional/**escaping**) + `printProposal` (iframe + `window.print`) + adapter local (`ProposalPort.export`) + integração no `Cesta` (botão → export) | ✅ |
| DB | MCP `postgres-ooh` | **vazio por design** (`db.ok = true`, sem checks) — tarefa de front | ✅ |

- **Toolchain:** runner via `npx vitest` (**fallback** — `bun` ausente no ambiente; `package.json` `test =
  "vitest run"`). Pendência operacional **viva desde EP3-01/EP4-01/EP4-04/EP4-05/EP4-06/EP4-09**: padronizar o
  runner (instalar `bun` ou fixar `vitest` no CI) p/ não depender de `npx`/`npm` ad-hoc.

## Decisões / desvios

- **Modo proposta + print, sem dep nova (decisão da task confirmada):** a view imprimível é montada como **string
  HTML** (`buildProposalHtml`) e impressa via **iframe + `window.print`** (`printProposal`) ⇒ salvar como PDF.
  **Quase zero dep**, coerente com o caráter de **stopgap** do F1. *(Alternativa descartada: `html2pdf`/`jspdf` —
  download em 1 clique mas +1 dep, sem ganho real pro F1.)*
- **Separação pura × efeito (testabilidade):** `buildProposalHtml` é **função pura** (cesta → HTML, determinística,
  sem DOM) e `printProposal` é o **efeito** (cria o iframe, injeta o HTML, chama `print`). Isso deixa a montagem do
  documento — capa, linhas, COMBINADO, **disclaimer incondicional**, mapa opcional — **100% testável** sem mexer no
  ambiente de impressão.
- **Escaping anti-injeção em todo texto dinâmico:** nome do cliente e nomes de linha são **escapados** antes de
  entrar no HTML. Como o documento é HTML montado por concatenação, isso fecha o vetor de injeção via dados da
  cesta — ponto que o review levantou e confirmou coberto.
- **Adapter local pluga no ponto já isolado (EP4-06/EP4-09):** a `ProposalPort` (`export(cesta): Promise<void>`)
  era servida por um **stub** desde o EP4-06; esta task troca o stub pelo **adapter local F1 real** **sem mexer**
  na tela — `Cesta.tsx`/`handleExport` ficam intactos. É exatamente a fronteira que os runs anteriores apontavam
  como "o EP4-07 pluga aqui". *(Evolução pós-F1, PRD §10: trocar o adapter por um que chame um agent com o
  template-padrão de proposta — sem tocar no resto.)*
- **Mapa da cesta opcional no PDF:** o `mapaSnapshot` (PNG do **EP4-09**) entra **se houver** — o documento não
  quebra quando a cesta foi exportada sem abrir o mapa; o snapshot, quando presente, carrega o **mesmo recorte
  honesto** (OTS ≠ alcance único) da tela.

## Honestidade do PDF (disclaimer incondicional)

- O **disclaimer SEMPRE presente** carimba: impressões = **estimativa (±35% / OTS)**, **não** pessoas únicas; o
  **ranking** é o número **defensável**; **vigências** das medições. É concatenado **incondicionalmente** no
  retorno de `buildProposalHtml` (`:150-167`, `:235`) — **não existe caminho** que gere o PDF sem ele. Coerente
  com a honestidade do COMBINADO (EP4-06/EP2-05) e do mapa da cesta (EP4-09) e com o EP4-08 (honestidade de UI):
  a proposta sai com o **mesmo recorte honesto** das telas que a originaram.

## Contract-check back↔front (review)

- **Fronteira interna `ProposalPort` (não chama o back):** o export é **client-side** — `createLocalProposalAdapter`
  consome o **payload da cesta** (linhas + COMBINADO já resolvidos pelo `CestaContext`/`/aggregate` do EP4-06) e o
  **`mapaSnapshot`** (EP4-09) **já no payload**; **nenhuma** chamada nova ao `uai-ooh-intel`. O contrato
  `ProposalPort.export(cesta)` é o mesmo do EP4-01 — esta task só substitui a **implementação** (stub → adapter
  local real).
- **Estado da cesta preservado:** o adapter **lê** o payload sem alterar a `CestaSelection` — barra/drawer/lista/
  ficha/mapa **não mudam de assinatura**. EP4-06 e EP4-09 seguem intactos.

## Estado do `dataset_version`

- **N/A — tarefa de front.** O `uai-portal` é o shell e **não acessa o banco `ooh`** nesta task; a validação de
  DB veio **vazia por design** (`db.ok = true`, sem checks). O export **não** chama o back — monta o PDF a partir
  do **payload da cesta** (linhas + COMBINADO + snapshot) já em mãos no cliente. Nenhum ciclo de versão é tocado
  aqui — promoção do `dataset_version` segue pendência do EP1/serving, não daqui. Os números reais da proposta
  (impressões somadas, %AB ponderado, faixa, trajetos no snapshot) só materializam com o `uai-ooh-intel` no ar
  (EP2-05/EP2-08/EP2-09/deploy) alimentando a cesta a montante.

## Adversarial — o que o cético tentou (refuted: false)

O vetor foi confrontado contra a **working tree atual** do `uai-portal`; **não derrubou** o done.

- **Vetor único (GATE/critério) — "os 3 critérios não estão (ou estão só parcialmente) cumpridos no repo
  `uai-portal`" (botão gera PDF modo proposta capa+linhas+COMBINADO+disclaimer; via `ProposalPort.export()`
  adapter local F1 client-side sem backend; disclaimer sempre presente).** **Não derrubou (refuted: false):**
  **(a)** **botão → geração** — `Cesta.tsx:269-278` (`ooh-cesta-export`) → `handleExport` (`:83`) →
  `port.export(payload)`. **(b)** **documento completo** — `buildProposalHtml` (`proposalDocument.ts:122`) monta
  **capa `.cover`** (cliente/BH Bus Mídia/data, `:212-221`), **`block--lines`** (`:223-228`), **`block--combined`**
  (`:230-233`) e **mapa** (snapshot EP4-09, opcional). **(c)** **disclaimer incondicional** — `disclaimerHtml`
  **CONCATENADO INCONDICIONALMENTE** no retorno (`:150-167`, `:235`), sem ramo que o omita. **(d)** **client-side
  via porta** — `createLocalProposalAdapter` implementa `ProposalPort.export()`, gera+imprime no cliente, **sem**
  chamada de back. Reforço: `npx vitest run` **152/152 AGORA**, dos quais **16/16** do EP4-07 (incluindo teste
  dedicado do disclaimer incondicional e do escaping).
- **Limite reconhecido (não derruba):** o que está provado é o **documento montado + disclaimer incondicional +
  fluxo de impressão (iframe/`window.print`) + escaping** por código e teste — **não** um PDF renderizado
  visualmente nem um print real disparado em headless (o `window.print`/iframe é o efeito mockado nos testes). E os
  **valores reais** da proposta (impressões, %AB, faixa, trajetos do snapshot) só aparecem com o `uai-ooh-intel`
  no ar alimentando a cesta a montante (EP2-05/EP2-08/EP2-09/deploy) — aqui se prova o **modo proposta + wiring**,
  não um render contra dados servidos ao vivo.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/epicos/runs/EP4-07-export-pdf.md`).
- Código no repo-alvo: `uai-portal` (ex-`uai-spark`) @ `feat/ooh-ep4-07` — módulo `/ooh`: `proposalDocument.ts`
  (`buildProposalHtml` — função pura: capa/linhas/COMBINADO/disclaimer incondicional/mapa opcional/escaping),
  `printProposal` (efeito — iframe + `window.print`), `createLocalProposalAdapter` (adapter local F1 real plugado
  na `ProposalPort`), integração do botão em `Cesta.tsx` (`ooh-cesta-export` → `handleExport` → `port.export`);
  **16 testes novos** na suíte (152/152).
- Próximo: **EP4-08** (honestidade de UI) — único card de ação do EP4 ainda em aberto. Pendência operacional
  carregada: **padronizar o runner de testes** (`bun`/vitest no CI) p/ não depender de `npx`/`npm` ad-hoc.
