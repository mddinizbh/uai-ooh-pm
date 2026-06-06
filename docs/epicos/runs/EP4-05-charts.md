# Run — EP4-05 (front: charts interativos · chartsSlot da ficha) · 2026-06-06 · ✅ DONE

> Execução no `uai-portal` (ex-`uai-spark`, branch `feat/ooh-ep4-05`), preenchendo o **`chartsSlot`** da
> ficha (encaixe deixado pronto no EP4-03, par do `mapSlot` do EP4-04). Renderiza **3 charts interativos**
> consumindo **só o `/metrics`** já resolvido — **Demanda** (`BarChart` pax útil/sáb/dom), **Perfil de
> classe A–E** (`BarChart` da distribuição) e **5 sub-scores** (`RadarChart`, a "forma" da linha). Reuso
> alto: `recharts` + `ui/chart.tsx` **já estavam no spark** — nada de dependência nova. Container/
> presentacional espelhando o padrão do **`CorridorMapSlot`** do EP4-04: `LineCharts.tsx` é puro (recebe
> `metrics: LineMetrics`), `ChartsSlot.tsx` resolve via `useLineMetrics(lineId)` **reusando o cache do
> react-query** (mesma query key da ficha ⇒ **sem 2ª chamada** ao back) e trata loading/error/empty. Stack
> React 18 · TS · **recharts** · shadcn/ui. Tarefa **só de front** — **não toca o banco `ooh`** (validação
> de DB vazia por design). Task do chart F1: `docs/epicos/bloco1/f1/03-front/modulo-ooh/EP4-05-charts.md`.
>
> **Veredito:** os **2 critérios de pronto** passam, com a suíte **reproduzida** (não só alegada): `npx
> vitest run` ⇒ **106/106** (0 falhas, 0 skips), dos quais **8 novos do EP4-05** (`LineCharts` +
> `ChartsSlot`) e o restante (incl. os 98/98 do EP4-04) reverificado. Os 3 charts renderizam com **dados
> reais** de `metrics.demand`/`metrics.profile`/`metrics.score`, são **interativos** (`ChartTooltip` +
> `ChartTooltipContent` em **todos** os três) e plugam no `chartsSlot` da ficha
> (`ChartsSlot` → `PlanejamentoPage` → `LineFicha` → `LineFichaView`, seção `ooh-ficha-charts-slot`).
> Review **aprovou** (verdict APROVADO); a rodada de refute **não derrubou** o done. → **DONE**. Fecha a
> **tríade visual da ficha** (header+seções no EP4-03, mapa no EP4-04, charts aqui); restam os cards de
> ação do EP4 (cesta/export/honestidade).

## Critério de pronto vs. medido (working tree `uai-portal` @ `feat/ooh-ep4-05`)

| Critério de pronto | Esperado | Medido | Veredito |
|---|---|---|---|
| **3 charts** renderizam com **dados reais** do `/metrics` | Demanda + Perfil A–E + 5 sub-scores a partir do `LineMetrics` resolvido | `LineCharts.tsx` (≈l.106–167) renderiza **`ooh-chart-demanda`** (`BarChart` ← `demand.paxUtil/paxSab/paxDom`), **`ooh-chart-perfil`** (`BarChart` ← `profile.pctAb/…/pctDe`) e **`ooh-chart-score`** (`RadarChart` ← `score.sAlcance…sPoi`); dados vêm do `useLineMetrics` (sem mock) | ✅ |
| Charts **interativos** (tooltip no hover) | recharts tooltip em cada chart | **`ChartTooltip` + `ChartTooltipContent`** (de `ui/chart.tsx`) presentes nos **3** charts — não só no default; coberto pelos testes | ✅ |
| Plugam nos **slots da ficha** (EP4-03) | encaixe no `chartsSlot` | wiring `PlanejamentoPage` (`chartsSlot={<ChartsSlot lineId=… />}`) → `LineFicha` → `LineFichaView` seção **`ooh-ficha-charts-slot`** — testado | ✅ |
| **Reuso** (sem dependência nova) | `recharts` + `ui/chart.tsx` já no spark | nenhuma lib nova: charts em `recharts` já presente; `ChartTooltip`/`ChartContainer` de `ui/chart.tsx` existente | ✅ |
| **Sem 2ª chamada** ao back pela ficha | reuso do payload do `/metrics` | `ChartsSlot` chama `useLineMetrics(lineId)` na **mesma query key** que a ficha já usa ⇒ react-query serve do **cache**, sem round-trip extra | ✅ |

## Build / testes (escopo declarado)

| Gate | Comando | Resultado | Veredito |
|---|---|---|---|
| Suíte cheia | `npx vitest run` | **106/106** (0 falhas, 0 skips) — **+8** vs **98/98** do EP4-04 | ✅ |
| Testes novos (EP4-05) | `vitest` (recorte) | **8/8** — `LineCharts` (3 charts + tooltips + binds `demand`/`profile`/`score`) + `ChartsSlot` (loading/error/empty + encaixe no slot) (8 criados = 8 esperados) | ✅ |
| Regressão (EP4-04 + ficha) | `npx vitest run` | **98/98** do EP4-04 reverificados — `chartsSlot` preenchido **não quebra** mapa nem header/seções da ficha | ✅ |
| DB | MCP `postgres-ooh` | **vazio por design** (`db.ok = true`, sem checks) — tarefa de front | ✅ |

- **Sem novo chunk pesado (≠ EP4-04):** `recharts` já fazia parte do bundle do portal — diferente do
  maplibre do EP4-04, **não houve code-split** novo nem warning de chunk; o custo do chart é o do payload
  do `/metrics` que a ficha **já** busca.
- **Toolchain:** runner via `npx vitest` (**fallback** — `bun` ausente no ambiente; `package.json`
  `test = "vitest run"`). Pendência operacional **viva desde EP3-01/EP4-01/EP4-04**: padronizar o runner
  (instalar `bun` ou fixar `vitest` no CI) p/ não depender de `npx`/`npm` ad-hoc.

## Decisões / desvios

- **5 sub-scores como `RadarChart` (decisão da task confirmada):** o breakdown dos 5 eixos
  (`sAlcance…sPoi`) entra como **radar** — mostra a "forma" da linha (lê força/fraqueza, compara perfis)
  num gráfico só. *(Alternativa descartada: 5 barras — valor exato mais fácil, menos identidade visual.)*
- **Container/presentacional espelhando `CorridorMapSlot`:** `LineCharts.tsx` é **puro** (recebe
  `metrics: LineMetrics` já resolvido, zero fetch), `ChartsSlot.tsx` é o **container** que resolve dados e
  trata loading/error/empty — mesma divisão do EP4-04. Mantém o chart testável sem rede e o slot plugável.
- **Reuso do cache do react-query (sem 2ª chamada):** `ChartsSlot` usa `useLineMetrics(lineId)` na **mesma
  query key** da ficha (EP4-03 já busca os números do `/metrics`), então o react-query **serve do cache** —
  charts e números compartilham um único payload. Análogo ao "lazy `/geo`" do EP4-04, mas aqui o ganho é
  **dedup de fetch**, não code-split. *(Alternativa descartada: `ChartsSlot` refazer o fetch.)*
- **Perfil A–E direto do `/metrics`:** a distribuição de classe vem de `line_profile_demografico`
  **já agregada no `/metrics`** (`profile.pctAb/…/pctDe`) — o front **não** recalcula quintil nem toca o
  banco; consome o número resolvido (coerente com a recalibração pós-Épico 2 registrada no run do Épico 3).

## Contract-check back↔front (review)

- **`LineMetrics` → 3 charts:** as três visões mapeiam 1:1 sub-objetos do payload do `/metrics` do
  `uai-ooh-intel` (**EP2-03**): `demand` (pax útil/sáb/dom) → barras de Demanda; `profile`
  (`pctAb/…/pctDe`) → barras de Perfil A–E; `score` (`sAlcance…sPoi`) → radar dos 5 sub-scores. Nenhum
  campo novo pedido ao back — é o **mesmo contrato** que a ficha (EP4-03) já consome.
- **Encaixe na ficha:** os charts entram pelo `chartsSlot` (`ooh-ficha-charts-slot`) deixado pronto no
  EP4-03 — sem acoplar a ficha ao `recharts` (o slot recebe `ChartsSlot`, que injeta `LineCharts`).
- **Par do EP4-04:** `chartsSlot` (aqui) + `mapSlot` (EP4-04) são os dois encaixes da ficha; ambos seguem
  o mesmo padrão container/presentacional, completando as seções de **Análises** + **Trajeto**.

## Estado do `dataset_version`

- **N/A — tarefa de front.** O `uai-portal` é o shell e **não acessa o banco `ooh`** nesta task; a
  validação de DB veio **vazia por design** (`db.ok = true`, sem checks). Os números dos charts chegam ao
  front **via API do `uai-ooh-intel`** (`/metrics`, consumidor read-only do `serving` ACTIVE). Nenhum ciclo
  de versão é tocado aqui — promoção do `dataset_version` segue pendência do EP1/serving, não daqui. Os
  valores reais só preenchem os charts com o intel no ar (EP2-03/EP2-09/deploy).

## Adversarial — o que o cético tentou (refuted: false)

O vetor foi confrontado contra a **working tree atual** do `uai-portal`; **não derrubou** o done.

- **Vetor 1 (GATE/critério) — "os 3 charts não são reais/interativos ou não estão plugados no slot".**
  **Não derrubou:** **(a)** os **3 charts com dados reais** existem — `LineCharts.tsx` renderiza
  `ooh-chart-demanda` (`BarChart` ← `demand.paxUtil/Sab/Dom`), `ooh-chart-perfil` (`BarChart` ←
  `profile.pctAb/…/pctDe`) e `ooh-chart-score` (`RadarChart` ← `score.sAlcance…sPoi`), tudo a partir do
  `useLineMetrics` (sem mock de dados). **(b)** **interativos** — `ChartTooltip` + `ChartTooltipContent`
  nos **três** charts (não só o tooltip default). **(c)** **plugados** — wiring
  `PlanejamentoPage` (`chartsSlot={<ChartsSlot lineId=… />}`) → `LineFicha` → `LineFichaView`
  (`ooh-ficha-charts-slot`). Reforço: `npx vitest run` **106/106 AGORA**, dos quais **8/8** de chart.
- **Reforço empírico do reuso:** nenhuma dependência nova e **nenhum chunk extra** — `recharts`/`ui/chart.tsx`
  já no portal; e `ChartsSlot` **reusa o cache** do `useLineMetrics` da ficha (mesma query key), então o
  chartsSlot **não dispara** uma 2ª chamada ao `/metrics` — confirmado no design do container e nos testes
  de loading/error/empty.
- **Limite reconhecido (não derruba):** os valores reais (pax, %classe, sub-scores) só aparecem nos charts
  com o `uai-ooh-intel` no ar (EP2-03/EP2-09/deploy); o que está provado é o **wiring + interatividade +
  binds de dados** (`demand`/`profile`/`score` → charts, tooltips, encaixe no slot) por código e teste —
  não um render contra `/metrics` servido ao vivo.

## Tracking

- Run **canônico** gravado aqui no hub de PM
  (`/Users/marleydiniz/IdeaProjects/personal/uai/uai-ooh-pm/docs/epicos/runs/EP4-05-charts.md`).
- Código no repo-alvo: `uai-portal` (ex-`uai-spark`) @ `feat/ooh-ep4-05` — módulo `/ooh`:
  `src/modules/ooh/components/LineCharts.tsx` (presentacional: `BarChart` Demanda · `BarChart` Perfil A–E ·
  `RadarChart` 5 sub-scores, todos com `ChartTooltip`/`ChartTooltipContent`),
  `src/modules/ooh/components/ChartsSlot.tsx` (container: `useLineMetrics(lineId)` reusando cache do
  react-query, loading/error/empty, plugado no `chartsSlot` da ficha); testes `LineCharts` + `ChartsSlot`
  (**8** novos).
- Próximo: cards de **ação** do EP4 — **EP4-06** (cesta), **EP4-07** (export PDF), **EP4-08** (honestidade
  de UI), **EP4-09** (mapa da cesta). Pendência operacional carregada: **padronizar o runner de testes**
  (`bun`/vitest) p/ não depender de `npx`/`npm` ad-hoc.
